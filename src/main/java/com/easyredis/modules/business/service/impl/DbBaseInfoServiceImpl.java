package com.easyredis.modules.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.common.utils.Query;
import com.easyredis.modules.business.dao.DbBaseInfoDao;
import com.easyredis.modules.business.entity.DbBaseInfo;
import com.easyredis.modules.business.service.DbBaseInfoService;
import org.apache.commons.lang.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author Asynchronous
* @description 针对表【db_base_info】的数据库操作Service实现
* @createDate 2023-07-27 17:38:55
*/
@Service("dbBaseInfoService")
public class DbBaseInfoServiceImpl extends ServiceImpl<DbBaseInfoDao, DbBaseInfo>
    implements DbBaseInfoService {

    private RedissonClient redisson;

    private boolean connectManager(DbBaseInfo dbBaseInfo) {
        // 连接配置
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer();

        // 拼接URL
        server.setAddress("redis://" + dbBaseInfo.getIp() + ":" + dbBaseInfo.getPort());

        // 判断是否存在密码
        if (dbBaseInfo.getIsPassword() == 1) {
            server.setPassword(dbBaseInfo.getPassword());
        }

        redisson = Redisson.create(config);

        // 判断是否连接成功
        return testConnection(redisson);
    }

    private boolean testConnection(RedissonClient redisson) {
        // 判断是否连接成功
        try {
            // 这里执行一个简单的 Redis 操作，例如删除一个键，不影响 Redis 数据
            redisson.getBucket("testKey").delete();
            return true;
        } catch (RedisException e) {
            return false;
        }
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        String dbName = (String)params.get("dbName");
        String ip = "0".equals(params.get("ip")) ? null : (String)params.get("ip");

        IPage<DbBaseInfo> page = this.page(
                new Query<DbBaseInfo>().getPage(params),
                new QueryWrapper<DbBaseInfo>()
                        .like(StringUtils.isNotBlank(dbName),"name", dbName)
                        .eq(StringUtils.isNotBlank(ip),"ip", ip)
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
        String ip = (String)params.get("ip");
        String port = (String)params.get("port");
        DbBaseInfo baseInfo = this.getOne(new LambdaQueryWrapper<DbBaseInfo>().eq(DbBaseInfo::getIp, ip).eq(DbBaseInfo::getPort, port));
        return baseInfo != null;
    }

    @Override
    public List<Integer> showPort(Map<String, Object> params) {
        String ip = (String)params.get("ip");
        return this.list(new LambdaQueryWrapper<DbBaseInfo>().eq(DbBaseInfo::getIp, ip)).stream().map(DbBaseInfo::getPort).distinct().collect(Collectors.toList());
    }

    @Override
    public boolean connectServer(Map<String, Object> params) {
        String ip = (String)params.get("ip");
        String port = (String)params.get("port");
        DbBaseInfo dbBaseInfo = this.getOne(new LambdaQueryWrapper<DbBaseInfo>().eq(DbBaseInfo::getIp, ip).eq(DbBaseInfo::getPort, port));
        // 先检查当前Redisson是否已经连接，如果已经连接则断开当前连接
        if (testConnection(redisson)) {
            redisson.shutdown();
        }
        return connectManager(dbBaseInfo);
    }

    @Override
    public PageUtils connectedList() {
        // 获取数据库中所有的键
        RKeys keys = redisson.getKeys();
        Set<String> allKeys = (Set<String>) keys.getKeys();

        // 创建一个映射（map）来存储所有的键值对
        Map<String, Object> allData = new HashMap<>();

        // 遍历所有的键，获取对应的值
        for (String key : allKeys) {
            Object value = redisson.getBucket(key).get();
            System.out.println("Key: " + key + " Value: " + value);
            allData.put(key, value);
        }
        // todo 数据处理没完成
        return null;
    }
}




