package com.easyredis.modules.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.common.utils.Query;
import com.easyredis.modules.business.dao.DbBaseInfoDao;
import com.easyredis.modules.business.entity.DbBaseInfo;
import com.easyredis.modules.business.service.DbBaseInfoService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
* @author Asynchronous
* @description 针对表【db_base_info】的数据库操作Service实现
* @createDate 2023-07-27 17:38:55
*/
@Service("dbBaseInfoService")
public class DbBaseInfoServiceImpl extends ServiceImpl<DbBaseInfoDao, DbBaseInfo>
    implements DbBaseInfoService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        String dbName = (String)params.get("dbName");

        IPage<DbBaseInfo> page = this.page(
                new Query<DbBaseInfo>().getPage(params),
                new QueryWrapper<DbBaseInfo>()
                        .like(StringUtils.isNotBlank(dbName),"name", dbName)
        );

        return new PageUtils(page);
    }
}




