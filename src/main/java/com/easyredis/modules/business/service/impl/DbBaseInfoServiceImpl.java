package com.easyredis.modules.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.common.utils.Query;
import com.easyredis.modules.business.dao.DbBaseInfoDao;
import com.easyredis.modules.business.entity.DbBaseInfo;
import com.easyredis.modules.business.entity.RedisResponse;
import com.easyredis.modules.business.service.DbBaseInfoService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Asynchronous
 * @description 针对表【db_base_info】的数据库操作Service实现
 * @createDate 2023-07-27 17:38:55
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

        jedis = new Jedis(shardInfo);

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

        // 设置当前查询库
        jedis.select(Integer.parseInt(dataSource));

        // 游标起始索引
        int startIndex = (pageNum - 1) * pageSize;
        // 游标结束索引
        int endIndex = startIndex + pageSize - 1;

        // 分批次获取键
        String cursor = "0";
        ScanParams scanParams = new ScanParams().match("*").count(pageSize);
        // 批次数量
        int batchNumber = 0;
        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            List<String> keys = scanResult.getResult();

            // 如果当前批次属于要展示的页数范围内，则处理当前批次的键
            if (batchNumber >= startIndex / pageSize && batchNumber <= endIndex / pageSize) {
                // 处理当前批次的键
                for (String key : keys) {
                    String type = jedis.type(key);
                    // 根据当前键的不同类型，使用不同的方法获取值
                    String value = getValueByType(jedis,key,type);
                    RedisResponse redisResponse = new RedisResponse();
                    redisResponse.setKey(key);
                    redisResponse.setValue(value);
                    redisResponse.setType(type);

                    redisResponseList.add(redisResponse);
                }
            }
            // 获取下一个游标和更新批次数量
            cursor = scanResult.getCursor();
            batchNumber++;
        } while (!"0".equals(cursor) && batchNumber <= endIndex / pageSize);

        PageUtils pageUtils = new PageUtils();
        pageUtils.setList(redisResponseList);
        pageUtils.setTotalCount(jedis.keys("*").size());
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
                return jedis.zrange(key, 0, -1).toString();
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
        } finally {
            jedis.close();
        }
    }


}




