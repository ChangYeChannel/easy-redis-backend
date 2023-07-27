package com.easyredis.modules.business.controller;

import com.easyredis.common.utils.Constant;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.common.utils.R;
import com.easyredis.modules.business.entity.DbBaseInfo;
import com.easyredis.modules.business.service.DbBaseInfoService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/business")
public class BusinessController {
    @Autowired
    private DbBaseInfoService dbBaseInfoService;

    /**
     * 基本信息列表
     */
    @GetMapping("/base/list")
    public R baseList(@RequestParam Map<String, Object> params){
        PageUtils page = dbBaseInfoService.queryPage(params);

        return R.ok().put("page", page);
    }

}
