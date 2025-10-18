package com.cmsr.sicp.gateway.controller;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CachePenetrationProtect;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.CreateCache;
import com.cmsr.common.vo.ResultObj;
import com.cmsr.sicp.gateway.api.RefreshApi;
import com.cmsr.sicp.gateway.config.RouteInitializer;
import com.cmsr.sicp.gateway.support.factory.WhiteFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 *
 *
 * @author yrz [xuehanxin]
 * @since Created in 10:03 2021/5/10
 */
@RestController
@Slf4j
public class RefreshController extends BaseRet implements RefreshApi {

    /**
     * 白名单工厂
     */
    @Resource
    private WhiteFactory whiteFactory;

    /**
     * 服务注入
     */
    @Resource
    private RouteInitializer routeInitializer;

    /**
     * 缓存
     */
    @CreateCache(name = "gtwRefresh::", cacheType = CacheType.REMOTE, expire = 10)
    @CachePenetrationProtect
    private Cache<String, String> refresh;

    @Override
    public ResultObj<String> refreshRoutes(String user, String project) {
        checkHeader(user, project);
        boolean cache = refresh.putIfAbsent("configRefresh", user);
        if (cache) {
            try {
                routeInitializer.init();
            } catch (Exception e) {
                log.info("user：" + user + " project：" + project + " time：" + LocalDateTime.now().toString() + " 刷新网关配置【失败】");
                return success("操作异常", "刷新失败");
            }
            log.info("user：" + user + " project：" + project + " time：" + LocalDateTime.now().toString() + " 刷新网关配置【失败】");
            return success("操作成功", "刷新成功");
        } else  {
            log.info("user：" + user + " project：" + project + " time：" + LocalDateTime.now().toString() + " 刷新网关配置【失败】");
            return success("操作限制", "刷新限制，请稍后尝试操作");
        }
    }

    @Override
    public ResultObj<String> refreshWhite(String user, String project) {
        checkHeader(user, project);
        boolean cache = refresh.putIfAbsent("whiteRefresh", user);
        if (cache) {
            whiteFactory.afterPropertiesSet();
            log.info("user：" + user + " project：" + project + " time：" + LocalDateTime.now().toString() + " 刷新白名单配置【成功】");
            return success("操作成功", "操作成功");
        } else {
            log.info("user：" + user + " project：" + project + " time：" + LocalDateTime.now().toString() + " 刷新白名单配置【失败】");
            return success("操作限制", "刷新限制，请稍后尝试操作");
        }
    }
}
