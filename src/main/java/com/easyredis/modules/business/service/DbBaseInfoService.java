package com.easyredis.modules.business.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.modules.business.entity.DbBaseInfo;

import java.util.List;
import java.util.Map;

/**
* @author Asynchronous
* @description 针对表【db_base_info】的数据库操作Service
* @createDate 2023-07-27 17:38:55
*/
public interface DbBaseInfoService extends IService<DbBaseInfo> {

    PageUtils queryPage(Map<String, Object> params);

    void deleteBatch(Long[] ids);

    List<String> init();

    boolean checkPort(Map<String, Object> params);

    List<Integer> showPort(Map<String, Object> params);

    boolean connectServer(Map<String, Object> params);

    PageUtils connectedList();
}
