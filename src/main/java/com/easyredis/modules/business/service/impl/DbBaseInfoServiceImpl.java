package com.easyredis.modules.business.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.easyredis.modules.business.service.DbBaseInfoService;
import generator.domain.DbBaseInfo;
import generator.mapper.DbBaseInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author Asynchronous
* @description 针对表【db_base_info】的数据库操作Service实现
* @createDate 2023-07-27 17:38:55
*/
@Service
public class DbBaseInfoServiceImpl extends ServiceImpl<DbBaseInfoMapper, DbBaseInfo>
    implements DbBaseInfoService {

}




