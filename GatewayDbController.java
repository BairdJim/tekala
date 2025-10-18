package com.cmsr.sicp.gateway.controller;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmsr.common.exception.ServiceException;
import com.cmsr.common.vo.ResultObj;
import com.cmsr.sicp.gateway.api.GatewayFeignApi;
import com.cmsr.sicp.gateway.dto.Config;
import com.cmsr.sicp.gateway.dto.ConfigUp;
import com.cmsr.sicp.gateway.dto.UpWriteUrl;
import com.cmsr.sicp.gateway.dto.WhiteUrl;
import com.cmsr.sicp.gateway.enums.DeleteFlagEnum;
import com.cmsr.sicp.gateway.enums.GlobalConstants;
import com.cmsr.sicp.gateway.po.GatewayConfig;
import com.cmsr.sicp.gateway.po.WhiteList;
import com.cmsr.sicp.gateway.service.GatewayConfigService;
import com.cmsr.sicp.gateway.service.WhiteListService;
import com.cmsr.sicp.gateway.vo.PageVo;
import com.cmsr.sicp.gateway.vo.WhiteVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 网关服务
 *
 * @author guiye
 * @since 2020/3/30 14:41
 */
@RestController
@ConditionalOnProperty(value = "cmsr.gateway.model", havingValue = "db")
@Slf4j
public class GatewayDbController extends BaseRet implements GatewayFeignApi {

    /**
     * 网关配置服务
     */
    @Resource
    private GatewayConfigService gatewayConfigService;

    /**
     * 服务注入
     */
    @Resource
    private WhiteListService whiteListService;


    @Override
    public ResultObj<Page<GatewayConfig>> selectConfig(String user, String project, String gatewayId,
                                                       Integer routeType, Integer status, Integer current, Integer size) {
        checkHeader(user, project);
        if (!StringUtils.isEmpty(gatewayId)) {
            gatewayId = gatewayId.trim();
        }
        Page<GatewayConfig> page = new Page<>(current <= 0 ? 1 : current, size <= 0 ? 10 : size);
        return success("操作成功", gatewayConfigService.page(page, new QueryWrapper<GatewayConfig>()
                .like(StrUtil.isNotBlank(gatewayId), "gateway_id", gatewayId)
                .eq(routeType != null, "route_type", routeType)
                .eq(status != null, "status", status)
                .eq("delete_flag", 0)
                .orderByDesc("create_time", "id")));
    }

    @Override
    public ResultObj<String> saveConfig(String user, String project, Config config) {
        checkHeader(user, project);
        config.initFiled();
        config.poTrim();
        config.strip();
        if (GlobalConstants.findRoute(config.getUri(), config.getRouteType()) < 0) {
            throw new ServiceException("100003E", "路由类型和映射路径不匹配");
        }
        if (gatewayConfigService.count(new QueryWrapper<GatewayConfig>()
                .eq("gateway_id", config.getGatewayId())
                .eq("delete_flag", 0)
                .last("limit 1")) != 0) {
            throw new ServiceException("100005E", "网关配置id重复");
        }
        GatewayConfig gatewayConfig = JSONUtil.toBean(JSONUtil.toJsonStr(config), GatewayConfig.class);
        gatewayConfig.setCreateBy(project);
        gatewayConfig.setUpdateBy(user);
        if (!gatewayConfigService.save(gatewayConfig)) {
            throw new ServiceException("100002E", "新增失败");
        }
        return success("操作成功", "新增成功");
    }

    @Override
    public ResultObj<String> deleteConfigById(String user, String project, Long id) {
        if (id == null) {
            throw new ServiceException("100010E", "id不能为空");
        }
        checkHeader(user, project);
        if (project.equals(GlobalConstants.SYS)) {
            throw new ServiceException("100004E", "系统权限需要手动删除，请联系网关管理人员");
        }
        GatewayConfig gatewayConfig = GatewayConfig.builder()
                .deleteFlag(DeleteFlagEnum.DELETED.getFlag())
                .updateBy(user).build();
        boolean res = gatewayConfigService.update(gatewayConfig, new QueryWrapper<GatewayConfig>()
                .eq("id", id)
                .eq("create_by", project)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (!res) {
            throw new ServiceException("100002E", "删除失败，请检查数据权限或是否存在");
        }
        return success("操作成功", "删除成功");
    }

    @Override
    public ResultObj<String> updateConfigById(String user, String project, ConfigUp config) {
        checkHeader(user, project);
        config.initFiled();
        config.poTrim();
        config.strip();
        if (project.equals(GlobalConstants.SYS)) {
            throw new ServiceException("100004E", "系统权限需要手动更新，请联系网关管理人员");
        }
        if (GlobalConstants.findRoute(config.getUri(), config.getRouteType()) < 0) {
            throw new ServiceException("100003E", "路由类型和映射路径不匹配");
        }
        if (gatewayConfigService.count(new QueryWrapper<GatewayConfig>()
                .eq("gateway_id", config.getGatewayId())
                .eq("delete_flag", 0)
                .ne("id", config.getId())
                .last("limit 1")) != 0) {
            throw new ServiceException("100005E", "网关配置id重复");
        }
        GatewayConfig gatewayConfig = JSONUtil.toBean(JSONUtil.toJsonStr(config), GatewayConfig.class);
        gatewayConfig.setUpdateBy(user);
        if (!gatewayConfigService.update(gatewayConfig, new QueryWrapper<GatewayConfig>()
                .eq("id", gatewayConfig.getId())
                .eq("create_by", project)
                .eq("delete_flag", 0)
                .last("limit 1"))) {
            throw new ServiceException("100002E", "更新失败");
        }
        return success("操作成功", "更新成功");
    }

    @Override
    public ResultObj<Integer> selectUpdatedRoutes() {
        return success("查询成功", gatewayConfigService.selectUpdatedRoutes());
    }

    // ----------------------------------- 白名单 ----------------------------------------------

    @Override
    public ResultObj<PageVo<WhiteVo>> selectByCondition(String user, String project,
                                                        String uri, Integer current, Integer size) {
        checkHeader(user, project);
        if (!StringUtils.isEmpty(uri)) {
            uri = uri.trim();
        }
        Page<WhiteList> page = new Page<>(current <= 0 ? 1 : current, size <= 0 ? 10 : size);
        Page<WhiteList> listPage = whiteListService.page(page, new QueryWrapper<WhiteList>()
                .select("id", "uri", "updated_by", "updated_time")
                .in("project", project, GlobalConstants.SYS)
                .like(StrUtil.isNotBlank(uri), "uri", uri)
                .orderByDesc("created_time", "id"));
        List<WhiteVo> list = new ArrayList<>(size);
        if (CollectionUtil.isNotEmpty(listPage.getRecords())) {
            list = listPage.getRecords()
                    .stream()
                    .map(p -> JSONUtil.toBean(JSONUtil.toJsonStr(p), WhiteVo.class))
                    .collect(Collectors.toList());
        }
        return success("操作成功", PageVo.<WhiteVo>builder()
                .size(page.getSize())
                .current(page.getCurrent() <= 0 ? 1 : page.getCurrent())
                .total(page.getTotal())
                .pages(page.getPages())
                .obj(list)
                .build());
    }

    /**
     * 查询更新的白名单
     *
     * @return 更新的白名单条数
     */
    @Override
    public ResultObj<Integer> selectUpdatedWhiteList() {
        return success("查询更新白名单成功",whiteListService.selectUpdatedWhiteList());
    }

    @Override
    public ResultObj<String> saveWhite(String user, String project, WhiteUrl whiteUrl) {
        checkHeader(user, project);
        WhiteList whiteList = WhiteList.builder().uri(whiteUrl.getUri()).project(project).build().addUser(user);
        if (whiteListService.count(new QueryWrapper<WhiteList>()
                .eq("project", project)
                .eq("uri", whiteUrl.getUri())
                .last("limit 1")) != 0) {
            throw new ServiceException("100001E", "白名单已存在");
        }
        boolean res = whiteListService.save(whiteList);
        if (!res) {
            throw new ServiceException("100002E", "新增失败");
        }
        return success("操作成功", "新增成功");
    }

    @Override
    public ResultObj<String> deleteWhite(String user, String project, Long id) {
        if (id == null) {
            throw new ServiceException("100010E", "id不能为空");
        }
        checkHeader(user, project);
        if (project.equals(GlobalConstants.SYS)) {
            throw new ServiceException("100004E", "系统权限需要手动删除，请联系网关管理人员");
        }
        boolean res = whiteListService.remove(new QueryWrapper<WhiteList>()
                .eq("id", id)
                .eq("project", project)
                .last("limit 1"));
        if (!res) {
            throw new ServiceException("100002E", "删除失败，请检查数据是否存在");
        }
        return success("操作成功", "删除成功");
    }

    @Override
    public ResultObj<String> updateWhite(String user, String project, UpWriteUrl upWriteUrl) {
        checkHeader(user, project);
        if (project.equals(GlobalConstants.SYS)) {
            throw new ServiceException("100004E", "系统权限需要手动更新，请联系网关管理人员");
        }
        int count = whiteListService.count(new QueryWrapper<WhiteList>()
                .ne("id", upWriteUrl.getId())
                .eq("project", project)
                .eq("uri", upWriteUrl.getUri())
                .last("limit 1"));
        if (count != 0) {
            throw new ServiceException("100002E", "路径重复");
        }
        boolean res = whiteListService.update(WhiteList.builder()
                .uri(upWriteUrl.getUri())
                .build().upUser(user), new QueryWrapper<WhiteList>()
                .eq("id", upWriteUrl.getId())
                .eq("project", project)
                .last("limit 1"));
        if (!res) {
            throw new ServiceException("100002E", "更新失败");
        }
        return success("操作成功", "更新成功");
    }
}

/**
 * nacos配置
 */
@RestController
@ConditionalOnProperty(value = "cmsr.gateway.model", havingValue = "nacos")
@Slf4j
class GatewayNacosController extends BaseRet implements GatewayFeignApi {

    @Override
    public ResultObj<Page<GatewayConfig>> selectConfig(String user, String project, String gatewayId,
                                                       Integer routeType, Integer status, Integer current, Integer size) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    @Override
    public ResultObj<String> saveConfig(String user, String project, Config config) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    @Override
    public ResultObj<String> deleteConfigById(String user, String project, Long id) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    @Override
    public ResultObj<String> updateConfigById(String user, String project, ConfigUp configUp) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    @Override
    public ResultObj<Integer> selectUpdatedRoutes() {
        return success("nacos模式下禁止使用增删改查", 0);
    }

    @Override
    public ResultObj<PageVo<WhiteVo>> selectByCondition(String user, String project, String uri,
                                                        Integer current, Integer size) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    /**
     * 查询更新的白名单
     *
     * @return 更新的白名单条数
     */
    @Override
    public ResultObj<Integer> selectUpdatedWhiteList() {
        return success("nacos模式下禁止使用增删改查", 0);
    }

    @Override
    public ResultObj<String> saveWhite(String user, String project, WhiteUrl whiteUrl) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    @Override
    public ResultObj<String> updateWhite(String user, String project, UpWriteUrl upWriteUrl) {
        return success("nacos模式下禁止使用增删改查", null);
    }

    @Override
    public ResultObj<String> deleteWhite(String user, String project, Long id) {
        return success("nacos模式下禁止使用增删改查", null);
    }
}
