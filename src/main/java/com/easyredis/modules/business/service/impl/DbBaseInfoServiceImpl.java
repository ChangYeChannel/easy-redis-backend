package com.easyredis.modules.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.common.utils.Query;
import com.easyredis.modules.business.dao.DbBaseInfoDao;
import com.easyredis.modules.business.entity.DbBaseInfo;
import com.easyredis.modules.business.entity.DispositionResponse;
import com.easyredis.modules.business.entity.RedisResponse;
import com.easyredis.modules.business.service.DbBaseInfoService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Asynchronous
 * &#064;description  针对表【db_base_info】的数据库操作Service实现
 * &#064;createDate  2023-07-27 17:38:55
 */
@Service("dbBaseInfoService")
public class DbBaseInfoServiceImpl extends ServiceImpl<DbBaseInfoDao, DbBaseInfo>
        implements DbBaseInfoService {

    private Jedis jedis;

    /**
     * 解析 Redis 键名，获取其数据库编号。
     *
     * @param key 键名
     * @return 数据库编号，如果不包含数字则返回 0
     */
    private static int parseDbIndex(String key) {
        int start = key.indexOf('{') + 1;
        int end = key.indexOf('}', start);
        if (start > 0 && end > start) {
            String substr = key.substring(start, end);
            try {
                return Integer.parseInt(substr);
            } catch (NumberFormatException e) {
                // Ignore if not a number
            }
        }
        return 0;
    }

    /**
     * 连接具体redis服务管理方法
     *
     * @param dbBaseInfo 连接信息
     * @return 连接状态
     */
    private boolean connectManager(DbBaseInfo dbBaseInfo) {
        // 连接配置
        JedisShardInfo shardInfo = new JedisShardInfo(dbBaseInfo.getIp(), dbBaseInfo.getPort());

        // 判断是否存在密码
        if (dbBaseInfo.getIsPassword() == 1) {
            shardInfo.setPassword(dbBaseInfo.getPassword());
        }

        try {
            jedis = new Jedis(shardInfo);
        }catch (Exception e) {
            return false;
        }

        // 判断是否连接成功
        return testConnection(jedis);
    }

    /**
     * 测试连接是否建立方法
     *
     * @param jedis 当前连接对象
     * @return 连接状态
     */
    private boolean testConnection(Jedis jedis) {
        // 判断是否连接成功
        try {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        String dbName = (String) params.get("dbName");
        String ip = "0".equals(params.get("ip")) ? null : (String) params.get("ip");

        IPage<DbBaseInfo> page = this.page(
                new Query<DbBaseInfo>().getPage(params),
                new QueryWrapper<DbBaseInfo>()
                        .like(StringUtils.isNotBlank(dbName), "name", dbName)
                        .eq(StringUtils.isNotBlank(ip), "ip", ip)
        );

        return new PageUtils(page);
    }

    @Override
    public void deleteBatch(Long[] ids) {
        this.removeByIds(Arrays.asList(ids));
    }

    @Override
    public List<String> init() {
        return this.list().stream().map(DbBaseInfo::getIp).distinct().collect(Collectors.toList());
    }

    @Override
    public boolean checkPort(Map<String, Object> params) {
        String ip = (String) params.get("ip");
        String port = (String) params.get("port");
        DbBaseInfo baseInfo = this.getOne(new LambdaQueryWrapper<DbBaseInfo>().eq(DbBaseInfo::getIp, ip).eq(DbBaseInfo::getPort, port));
        return baseInfo != null;
    }

    @Override
    public List<Integer> showPort(Map<String, Object> params) {
        String ip = (String) params.get("ip");
        return this.list(new LambdaQueryWrapper<DbBaseInfo>().eq(DbBaseInfo::getIp, ip)).stream().map(DbBaseInfo::getPort).distinct().collect(Collectors.toList());
    }

    @Override
    public boolean connectServer(Map<String, Object> params) {
        String ip = (String) params.get("ip");
        String port = (String) params.get("port");
        DbBaseInfo dbBaseInfo = this.getOne(new LambdaQueryWrapper<DbBaseInfo>().eq(DbBaseInfo::getIp, ip).eq(DbBaseInfo::getPort, port));
        // 先检查当前Redisson是否已经连接
        if (jedis != null) {
            // 如果已经连接就判断当前连接是否与传入的连接参数一致，如果一致就保持连接，不一致就断开连接重新根据传入参数建立新的连接
            if (!ip.equals(jedis.getClient().getHost()) || Integer.parseInt(port) != jedis.getClient().getPort()) {
                jedis.close();
                return connectManager(dbBaseInfo);
            }
            return true;
        }
        return connectManager(dbBaseInfo);
    }

    @Override
    public PageUtils connectedList(Map<String, Object> params) {
        // 创建一个存储 RedisResponse 对象的 List
        List<RedisResponse> redisResponseList = new ArrayList<>();

        String dataSource = (String) params.get("dataSource");
        int pageNum = Integer.parseInt((String) params.get("page"));
        int pageSize = Integer.parseInt((String) params.get("limit"));
        String likeKey = (String) params.get("likeKey");

        // 设置当前查询库
        if (jedis == null) {
            return null;
        }
        jedis.select(Integer.parseInt(dataSource));

        // 分批次获取键
        String cursor = "0";
        ScanParams scanParams = new ScanParams();

        // 模糊查询
        if (likeKey != null && !"".equals(likeKey)) {
            scanParams.match("*" + likeKey + "*").count(10000);
        } else {
            scanParams.match("*").count(10000);
        }

        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            cursor = scanResult.getCursor();
            List<String> keys = scanResult.getResult();

            for (String key : keys) {
                String type = jedis.type(key);
                String value = getValueByType(jedis,key,type);
                RedisResponse redisResponse = new RedisResponse();
                redisResponse.setKey(key);
                redisResponse.setValue(value);
                redisResponse.setType(type);

                // 设置当前键的过期时间
                Long ttl = jedis.ttl(key);
                if (ttl > 0) {
                    redisResponse.setTtl(ttl);
                } else if (ttl == -1) {
                    redisResponse.setTtl(-1L);
                } else {
                    redisResponse.setTtl(-99L);
                }

                redisResponseList.add(redisResponse);
            }

        } while (!"0".equals(cursor));

        // 分页处理
        int total = redisResponseList.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        if (pageNum > totalPages) {
            pageNum = totalPages;
        }

        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, total);

        List<RedisResponse> dataList = redisResponseList.subList(startIndex, endIndex);

        PageUtils pageUtils = new PageUtils();
        pageUtils.setList(dataList);
        pageUtils.setTotalCount(redisResponseList.size());
        return pageUtils;
    }

    private static String getValueByType(Jedis jedis, String key, String type) {
        switch (type) {
            case "string":
                return jedis.get(key);
            case "list":
                return jedis.lrange(key, 0, -1).toString();
            case "hash":
                return jedis.hgetAll(key).toString();
            case "set":
                return jedis.smembers(key).toString();
            case "zset":
                StringBuilder result = new StringBuilder("[");
                for (String value : jedis.zrange(key, 0, -1)) {
                    result.append("[").append(value).append(":");
                    Double score = jedis.zscore(key, value);
                    result.append(score).append("],");
                }
                result.deleteCharAt(result.length() - 1);
                result.append("]");
                return String.valueOf(result);
            default:
                return "键类型未知，无法获取值信息";
        }
    }

    @Override
    public String getConnectDatabasesCount() {
        try {
            // 获取 Redis 服务器数据库数量配置参数
            List<String> config = jedis.configGet("databases");
            return config.get(1);
        } catch (Exception e) {
            jedis.quit();
            return "";
        }
    }

    @Override
    public String deleteByKeys(String[] ids) {
        // 拿到当前操作库
        String dataSource = ids[0];

        String[] newIds = Arrays.copyOfRange(ids, 1, ids.length);

        if (jedis == null) {
            return null;
        }
        jedis.select(Integer.parseInt(dataSource));
        long result = jedis.del(newIds);

        String resultMessage;
        if (result == newIds.length) {
            resultMessage = "删除成功！";
        } else if (result == 0) {
            resultMessage = "删除失败！";
        } else if (result == -1) {
            resultMessage = "删除过程出现异常！";
        } else {
            resultMessage = "部分删除成功！";
        }
        return resultMessage;
    }

    @Override
    public RedisResponse getValueByKey(String key,Map<String, Object> params) {
        String dataSource = (String) params.get("dataSource");
        if (jedis == null) {
            return null;
        }
        jedis.select(Integer.parseInt(dataSource));

        RedisResponse redisResponse = new RedisResponse();

        String type = jedis.type(key);
        String resultType;

        switch (type) {
            case "string":
                resultType = "0";break;
            case "list":
                resultType = "1";break;
            case "hash":
                resultType = "4";break;
            case "set":
                resultType = "2";break;
            case "zset":
                resultType = "3";break;
            default:
                resultType = "";
        }

        // 根据当前键的不同类型，使用不同的方法获取值
        String value = getValueByType(jedis,key,type);

        redisResponse.setValue(value);
        redisResponse.setType(resultType);

        Long ttl = jedis.ttl(key);
        if (ttl > 0) {
            redisResponse.setTtl(ttl);
        } else if (ttl == -1) {
            redisResponse.setIsTTL("0");
        } else {
            redisResponse.setIsTTL("-1");
        }
        return redisResponse;
    }

    @Override
    public boolean redisSave(RedisResponse redisResponse) {
        String datasource = redisResponse.getDatasource();
        String key = redisResponse.getKey();
        String value = redisResponse.getValue();
        String type = redisResponse.getType();
        Long ttl = redisResponse.getTtl();
        String isTTL = redisResponse.getIsTTL();

        if (jedis == null) {
            return false;
        }
        jedis.select(Integer.parseInt(datasource));
        jedis.del(key);

        String[] readValue;
        if (value.startsWith("[") || value.startsWith("{") && value.endsWith("]") || value.endsWith("}")) {
            readValue = value.substring(1, value.length() - 1).split(",");
        } else {
            readValue = value.split(",");
        }
        // 设置不同的解析策略
        switch (type) {
            case "0":
                jedis.set(key, value);
                break;
            case "1":
                try {
                    jedis.lpush(key,readValue);
                } catch (Exception e) {
                    return false;
                }
                break;
            case "2":
                try {
                    jedis.sadd(key,readValue);
                } catch (Exception e) {
                    return false;
                }
                break;
            case "3":
                try {
                    Map<String, Double> zsetMap = new HashMap<>();
                    for (String item : readValue) {
                        String[] pair = item.substring(1, item.length() - 1).split(":");
                        String resultValue = pair[0].trim();
                        double score = Double.parseDouble(pair[1].trim());
                        zsetMap.put(resultValue, score);
                    }
                    jedis.zadd(key,zsetMap);
                } catch (Exception e) {
                    return false;
                }
                break;
            case "4":
                try {
                    Map<String, String> hashMap = new HashMap<>();
                    for (String item : readValue) {
                        String[] pair = item.split("=");
                        String field = pair[0].trim();
                        String resultValue = pair[1].trim();
                        hashMap.put(field, resultValue);
                    }
                    jedis.hmset(key,hashMap);
                } catch (Exception e) {
                    return false;
                }
                break;
            default:
                break;
        }

        if ("1".equals(isTTL)) {
            // 设置键的过期时间
            try {
                jedis.expire(key, ttl);
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean checkKey(Map<String, Object> params) {
        String dataSource = (String) params.get("dataSource");
        String key = (String) params.get("key");

        if (jedis == null) {
            return false;
        }
        jedis.select(Integer.parseInt(dataSource));

        return jedis.exists(key);
    }

    @Override
    public boolean checkConnected() {
        return testConnection(jedis);
    }

    @Override
    public List<DispositionResponse> dispositionList() {
        if (jedis == null) {
            return null;
        }
        List<DispositionResponse> responses = new ArrayList<>();
        List<String> configs = jedis.configGet("*");

        for (int i = 0; i < configs.size(); i++) {
            DispositionResponse dispositionResponse = new DispositionResponse();
            dispositionResponse.setKey(configs.get(i));
            dispositionResponse.setValue(configs.get(++i));
            responses.add(dispositionResponse);
        }

        return responses;
    }
}