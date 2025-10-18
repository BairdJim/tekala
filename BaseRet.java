package com.cmsr.sicp.gateway.controller;

import cn.hutool.core.util.StrUtil;
import com.cmsr.common.enums.ResultCodeEnum;
import com.cmsr.common.exception.ServiceException;
import com.cmsr.common.vo.ResultObj;

/**
 * 基础返回
 *
 * @author yrz [xuehanxin]
 * @since Created in 11:17 2021/4/25
 */
public class BaseRet {

    /**
     * 构造
     */
    public BaseRet() {
    }

    /**
     * 成功
     *
     * @param msg msg
     * @param t t
     * @param <T> T
     * @return ret
     */
    protected <T> ResultObj<T> success(String msg, T t) {
        return this.build(ResultCodeEnum.success.getCode(), msg, t);
    }

    /**
     * 放回体
     *
     * @param code code
     * @param msg msg
     * @param t t
     * @param <T> T
     * @return ret
     */
    private <T> ResultObj<T> build(String code, String msg, T t) {
        ResultObj<T> result = new ResultObj<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(t);
        return result;
    }

    // 校权工具
    /**
     * header检查
     *
     * @param user user
     * @param project project
     */
    protected void checkHeader(String user, String project) {
        if (StrUtil.isBlank(user)) {
            throw new ServiceException("500", "header need user_id");
        }
        if (StrUtil.isBlank(project)) {
            throw new ServiceException("501", "header need project_id");
        }
    }
}
