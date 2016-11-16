#redis java封装

**20161026 更新**

1 增加increment操作，原子的++，只能在int or long类型时使用

2 增加flushDb操作

---

主要需要实现几个功能：
1 数据获取
2 数据缓存
3 分布式锁
4 带锁的数据处理

为了实现这几个功能，对redis做了一层相对很薄的封装，过程如下：

1 首先，我们需要定义redis的访问接口，如下：
```
//数据查询,更新接口
public interface DataAccess<T> {
    void set(String key, T value);

    void set(String key, T value, int validTime);

    T get(String key);

    List<T> popAll(String key);

    void push(String key, T value);

    //validTime = -1 表示无失效时间
    boolean setNX(String key, T value, int validTime);

    void delete(String key);

    boolean tryLock(String key, T value, int timeout);

    void unLock(String key);
}
```
接口的实现我们基于构建匿名类的方式，数据访问基于RedisTemplate, 代码如下：
```
public class RedisAccessBuilder<T> {
    private RedisTemplate<String, T> redisTemplate;

    public RedisAccessBuilder(RedisTemplate<String, T> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //1 设置json序列化方法
    //2 设置redis访问器
    //3 构造匿名类
    public DataAccess<T> buildDataAccess(Class<T> clazz, final DbDataGet<T> dbDataGet) {
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        //1
        if (clazz.equals(String.class)) {
            redisTemplate.setValueSerializer(new StringRedisSerializer());
        } else {
            redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<T>(clazz));
        }

        //2
        final ValueOperations<String, T> valueOperations= redisTemplate.opsForValue();
        final ListOperations<String, T> listOperations = redisTemplate.opsForList();

        //3
        return new DataAccess<T>() {
            @Override
            public void set(String key, T value) {
                valueOperations.set(key, value);
            }

            @Override
            public void set(String key, T value, int validTime) {
                set(key, value);
                redisTemplate.expire(key, validTime, TimeUnit.SECONDS);
            }

            @Override
            public T get(String key) {
                T data = valueOperations.get(key);

                //如果redis中没有且设置了数据库源,则从数据库中获取
                if (null == data
                        && null != dbDataGet) {
                    data = dbDataGet.get(key);

                    if (null != data) {
                        //默认保存一天
                        set(key, data, Constants.ONE_DAY);
                    }

                    try {
                        //避免数据库压力过大
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                return data;
            }

            @Override
            public List<T> popAll(String key) {
                String listKey = "list#" + key;
                List<T> resList = listOperations.range(listKey, 0, -1);

                delete(listKey);

                return resList;
            }

            @Override
            public void push(String key, T value) {
                String listKey = "list#" + key;
                listOperations.rightPush(listKey, value);
            }

            @Override
            public boolean setNX(String key, T value, int validTime) {
                if (valueOperations.setIfAbsent(key, value)) {
                    //todo throw exception
                    if (validTime > 0) {
                        return redisTemplate.expire(key, validTime, TimeUnit.SECONDS);
                    } else {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public void delete(String key) {
                redisTemplate.delete(key);
            }

            @Override
            public boolean tryLock(String key, T value, int timeout) {
                String lockKey = "locked#" + key;
                long startTime = System.currentTimeMillis();
                int sleepTimes = 0;

                while (!setNX(lockKey, value, Constants.MAX_LOCK_TIME)) {
                    try {
                        Thread.sleep(1);

                        sleepTimes++;

                        if (sleepTimes % 100 == 0) {
                            //超时判断
                            if (System.currentTimeMillis() - startTime > Constants.MAX_TIMEOUT * 1000) {
                                return false;
                            }
                        }
                    } catch (InterruptedException e) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public void unLock(String key) {
                String lockKey = "locked#" + key;

                delete(lockKey);
            }
        };
    }
}
```
在有了redis访问接口之后，我们基于它，可以继续定制redis缓存
2 redis缓存
我们的redis缓存分为两种：
a 类似hashset，判断一个key是否存在，实现如下：
```
//用redis实现的有时效缓存封装
public class RedisCache {
    private DataAccess<String> dataAccess;

    public RedisCache(RedisTemplate<String, String> redisTemplate) {
        RedisAccessBuilder<String> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);

        dataAccess = redisAccessBuilder.buildDataAccess(String.class, null);
    }

    //如果不存在,则插入并返回true
    //否则返回false
    public boolean cacheIfAbsent(String key, int validSecond) {
        String cacheKey = "cached#" + key;
        String cacheValue = "" + System.currentTimeMillis();

        return dataAccess.setNX(cacheKey, cacheValue, validSecond);
    }
}
```
b 类似hashtable，通过一个key，查询value，为了保证数据的时效性，在实现中使用异步的方式从数据中查询数据，保证当前数据的有效性，同时为了保证效率，使用一个基于内存的hashmap来做第一层缓存，代码如下：
```
public class MemCache<T> {
    private Map<String, T> map = new ConcurrentHashMap<String, T>();
    private DataAccess<T> dataAccess;
    private DbDataGet<T> dbDataGet;

    public MemCache(RedisTemplate<String, T> redisTemplate, Class<T> clazz, final DbDataGet<T> dbDataGet) {
        RedisAccessBuilder<T> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);
        dataAccess = redisAccessBuilder.buildDataAccess(clazz, dbDataGet);
        this.dbDataGet = dbDataGet;

        asynUpdateData();
    }

    //1 检查内存
    //2 查询redis(redis没有则查询数据库)
    public T get(String key) {
        //1
        T value = map.get(key);

        if (null == value) {
            //2
            value = dataAccess.get(key);
            map.put(key, value);
        }

        return value;
    }

    //异步更新数据,主要针对数据库中的数据被修改,造成和redis及内存不一致的情况
    //1 创建新的map
    //2 从原map中遍历数据
    //3 从数据库中获取数据,判断是否和内存中一致
    //4 如果不一致,则更新内存及redis中的数据
    //5 将新的map作为当前的map
    private void asynUpdateData() {
        ExecutorService service = Executors.newFixedThreadPool(1);

        service.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    int times = 0;

                    try {
                        //1
                        Map<String, T> newMap = new ConcurrentHashMap<>();

                        //2
                        for (String key : map.keySet()) {
                            times++;

                            //3
                            if (null != dataAccess.get(key)) {
                                T value = dbDataGet.get(key);

                                //4
                                if (value != map.get(key)) {
                                    newMap.put(key, value);
                                    dataAccess.set(key, value, Constants.ONE_DAY);
                                }
                            }

                            //每100条控制一下时间,避免数据库压力过大
                            if (times % 100 == 0) {
                                Thread.sleep(100);
                            }
                        }

                        //5
                        map = newMap;

                        Thread.sleep(Constants.FIVE_MINUTES);
                    } catch (InterruptedException e) {
                        log.info("interrupted " + e);
                    } catch (Exception e) {
                        log.info("exception " + e);
                    }
                }
            }
        });
    }
}
```
3&4 数据处理和分布式锁我们放在一起处理，首先定义数据处理接口
```
//数据处理接口
public interface DataProcess<T> {
    DetailRes process(String key, T data);
}
```
之后定义带锁的数据处理接口
```
//锁住key后执行数据处理
public interface DataProcessWithLock<T> {
    DetailRes execute(String key, T data);
}
```
我们需要用户自己实现DataProcess接口，并在构建DataProcessWithLock的时候传入，构建DataProcessWithLock的代码如下：
```
//带锁的数据处理封装
public class RedisDataProcessWithLockBuilder {
    private DataAccess<String> dataAccess;

    public RedisDataProcessWithLockBuilder(RedisTemplate<String, String> redisTemplate) {
        RedisAccessBuilder<String> redisAccessBuilder = new RedisAccessBuilder<>(redisTemplate);

        dataAccess = redisAccessBuilder.buildDataAccess(String.class, null);
    }

    public <T> DataProcessWithLock<T> buildDataProcessWithLock(final DataProcess<T> dataProcess) {
        return new DataProcessWithLock<T>() {
            @Override
            public DetailRes execute(String key, T data) {
                //value为当前时间,方便追查问题
                String lockValue = "" + System.currentTimeMillis();

                try {
                    if (dataAccess.tryLock(key, lockValue, Constants.MAX_LOCK_TIME)) {
                        return dataProcess.process(key, data);
                    } else {
                        return new DetailRes(false, key + " locked failed");
                    }
                } catch (Exception e) {
                    return new DetailRes(false, key + " " + e.toString());
                } finally {
                    dataAccess.unLock(key);
                }
            }
        };
    }
}
```
有了前面的各种准备之后，我们用一个例子做结。
首先定义一个model：
```
@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {
    long userId;
    String name;
}
```
在对这个model处理前，我们要按userId将其锁住，之后的处理就是简单的将这个user打印出来，代码如下：
```
@Service
public class RedisExample {
    @Autowired
    RedisTemplate<String, String> stringRedisTemplate;

    @Autowired
    RedisTemplate<String, User> userRedisTemplate;

    private DataProcessWithLock<User> dataProcessWithLock;

    @PostConstruct
    public void init() {
        RedisDataProcessWithLockBuilder redisDataProcessWithLockBuilder =
                new RedisDataProcessWithLockBuilder(stringRedisTemplate);

        dataProcessWithLock = redisDataProcessWithLockBuilder.buildDataProcessWithLock(new DataProcess<User>() {
            //用户自己的实现
            @Override
            public DetailRes process(String key, User user) {
                System.out.println(user);

                return new DetailRes(true, "");
            }
        });
    }

    public void process() {
        User user = new User(111, "littlersmall");
        String key = "" + user.getUserId();

        dataProcessWithLock.execute(key, user);
    }
}
```

**github地址如下**
>https://github.com/littlersmall/redis-access
