package com.easyredis.modules.business.controller;

import com.easyredis.common.annotation.SysLog;
import com.easyredis.common.utils.PageUtils;
import com.easyredis.common.utils.R;
import com.easyredis.common.validator.ValidatorUtils;
import com.easyredis.common.validator.group.AddGroup;
import com.easyredis.common.validator.group.UpdateGroup;
import com.easyredis.modules.business.entity.DbBaseInfo;
import com.easyredis.modules.business.entity.RedisResponse;
import com.easyredis.modules.business.service.DbBaseInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business")
public class BusinessController {
    @Autowired
    private DbBaseInfoService dbBaseInfoService;

    /**
     * 数据库基本信息列表
     */
    @GetMapping("/base/list")
    public R baseList(@RequestParam Map<String, Object> params){
        PageUtils page = dbBaseInfoService.queryPage(params);

        return R.ok().put("page", page);
    }

    /**
     * 数据库基本信息检查，保持IP地址联合端口号唯一性
     */
    @GetMapping("/base/checkPort")
    public R checkPort(@RequestParam Map<String, Object> params){
        boolean isExits = dbBaseInfoService.checkPort(params);

        return R.ok().put("isExits", isExits);
    }

    /**
     * 初始化主界面的IP地址查询框
     */
    @GetMapping("/base/init")
    public R init(){
        List<String> ips = dbBaseInfoService.init();
        return R.ok().put("ips", ips);
    }

    /**
     * 根据传入的IP地址，确定当前IP地址下存在的端口号
     */
    @GetMapping("/base/showPort")
    public R showPort(@RequestParam Map<String, Object> params){
        List<Integer> ports = dbBaseInfoService.showPort(params);
        return R.ok().put("ports", ports);
    }

    /**
     * 获取某个数据库基本信息
     */
    @GetMapping("/base/info/{id}")
    public R info(@PathVariable("id") Long id){
        DbBaseInfo baseInfo = dbBaseInfoService.getById(id);
        return R.ok().put("baseInfo", baseInfo);
    }

    /**
     * 删除数据库基本信息
     */
    @SysLog("删除数据库基本信息")
    @PostMapping("/base/delete")
    public R delete(@RequestBody Long[] ids){
        dbBaseInfoService.deleteBatch(ids);
        return R.ok();
    }

    /**
     * 保存数据库基本信息
     */
    @SysLog("保存数据库基本信息")
    @PostMapping("/base/save")
    public R save(@RequestBody DbBaseInfo dbBaseInfo){
        ValidatorUtils.validateEntity(dbBaseInfo, AddGroup.class);
        dbBaseInfo.setCreateTime(new Date());
        dbBaseInfo.setUpdateTime(new Date());
        if (dbBaseInfo.getIsPassword() == 0) {
            dbBaseInfo.setPassword("");
        }
        dbBaseInfoService.save(dbBaseInfo);
        return R.ok();
    }

    /**
     * 修改数据库基本信息
     */
    @SysLog("修改数据库基本信息")
    @PostMapping("/base/update")
    public R update(@RequestBody DbBaseInfo dbBaseInfo){
        ValidatorUtils.validateEntity(dbBaseInfo, UpdateGroup.class);
        dbBaseInfo.setUpdateTime(new Date());
        if (dbBaseInfo.getIsPassword() == 0) {
            dbBaseInfo.setPassword("");
        }
        dbBaseInfoService.updateById(dbBaseInfo);
        return R.ok();
    }

    /**
     * 根据传入的参数连接指定数据库服务
     */
    @GetMapping("/databases/connectServer")
    public R connectServer(@RequestParam Map<String, Object> params){
        boolean connectStatus = dbBaseInfoService.connectServer(params);
        if (connectStatus) {
            String count = dbBaseInfoService.getConnectDatabasesCount();
            return R.ok().put("count",count);
        }else {
            return R.error("连接失败！请检查连接信息");
        }
    }

    /**
     * 具体数据库连接信息列表
     */
    @GetMapping("/databases/list")
    public R connectedList(@RequestParam Map<String, Object> params){
        PageUtils datalist = dbBaseInfoService.connectedList(params);

        return R.ok().put("datalist", datalist);
    }

    /**
     * 删除redis中数据
     */
    @SysLog("删除redis中数据")
    @PostMapping("/databases/delete")
    public R redisKeyDelete(@RequestBody String[] ids){
        String resultMessage = dbBaseInfoService.deleteByKeys(ids);
        return R.ok().put("message", resultMessage);
    }

    /**
     * 获取某个Key的信息
     */
    @GetMapping("/databases/info/{key}")
    public R info(@PathVariable("key") String key,@RequestParam Map<String, Object> params){
        RedisResponse redisResponse = dbBaseInfoService.getValueByKey(key,params);
        return R.ok().put("RedisResponse", redisResponse);
    }

    /**
     * 保存或更新某个Key的信息
     */
    @PostMapping("/databases/save")
    public R redisSave(@RequestBody RedisResponse redisResponse){
        dbBaseInfoService.redisSave(redisResponse);
        return R.ok();
    }
}
