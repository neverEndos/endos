package cn.iocoder.yudao.framework.banner.core;

import cn.hutool.core.thread.ThreadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.ClassUtils;

import java.util.concurrent.TimeUnit;

/**
 * 项目启动成功后，输出模块状态
 *
 * @author 芋道源码
 */
@Slf4j
public class BannerApplicationRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        ThreadUtil.execute(() -> {
            ThreadUtil.sleep(1, TimeUnit.SECONDS); // 延迟 1 秒，保证输出到结尾
            log.info("项目启动成功！");

            // 工作流
            if (isNotPresent("cn.iocoder.yudao.module.bpm.framework.flowable.config.BpmFlowableConfiguration")) {
                System.out.println("[工作流模块 yudao-module-bpm - 已禁用]");
            }
            // 支付平台
            if (isNotPresent("cn.iocoder.yudao.module.pay.framework.pay.config.PayConfiguration")) {
                System.out.println("[支付系统 yudao-module-pay - 已禁用]");
            }
            // 数据报表
            if (isNotPresent("cn.iocoder.yudao.module.report.framework.security.config.SecurityConfiguration")) {
                System.out.println("[报表模块 yudao-module-report - 已禁用]");
            }
            // 微信公众号
            if (isNotPresent("cn.iocoder.yudao.module.mp.framework.mp.config.MpConfiguration")) {
                System.out.println("[微信公众号 yudao-module-mp - 已禁用]");
            }
            // 商城系统
            if (isNotPresent("cn.iocoder.yudao.module.trade.framework.web.config.TradeWebConfiguration")) {
                System.out.println("[商城系统 yudao-module-mall - 已禁用]");
            }
            // ERP 系统
            if (isNotPresent("cn.iocoder.yudao.module.erp.framework.web.config.ErpWebConfiguration")) {
                System.out.println("[ERP 系统 yudao-module-erp - 已禁用]");
            }
            // WMS 仓库管理系统
            if (isNotPresent("cn.iocoder.yudao.module.wms.framework.web.config.WmsWebConfiguration")) {
                System.out.println("[WMS 仓库管理系统 yudao-module-wms - 已禁用]");
            }
            // CRM 系统
            if (isNotPresent("cn.iocoder.yudao.module.crm.framework.web.config.CrmWebConfiguration")) {
                System.out.println("[CRM 系统 yudao-module-crm - 已禁用]");
            }
            // MES 系统
            if (isNotPresent("cn.iocoder.yudao.module.mes.framework.web.config.MesWebConfiguration")) {
                System.out.println("[MES 系统 yudao-module-mes - 已禁用]");
            }
            // HRM 人力资源管理系统
            if (isNotPresent("cn.iocoder.yudao.module.hrm.framework.web.config.HrmWebConfiguration")) {
                System.out.println("[HRM 人力资源管理系统 yudao-module-hrm - 已禁用]");
            }
            // FMS 财务管理系统
            if (isNotPresent("cn.iocoder.yudao.module.fms.framework.web.config.FmsWebConfiguration")) {
                System.out.println("[FMS 财务管理系统 yudao-module-fms - 已禁用]");
            }
            // PMS 系统
            if (isNotPresent("cn.iocoder.yudao.module.pms.framework.web.config.PmsWebConfiguration")) {
                System.out.println("[PMS 项目管理系统 yudao-module-pms - 已禁用]");
            }
            // OA 办公自动化
            if (isNotPresent("cn.iocoder.yudao.module.oa.framework.config.OaProperties")) {
                System.out.println("[OA 协同办公 yudao-module-oa - 已禁用]");
            }
            // AI 大模型
            if (isNotPresent("cn.iocoder.yudao.module.ai.framework.web.config.AiWebConfiguration")) {
                System.out.println("[AI 大模型 yudao-module-ai - 已禁用]");
            }
            // IoT 物联网
            if (isNotPresent("cn.iocoder.yudao.module.iot.framework.web.config.IotWebConfiguration")) {
                System.out.println("[IoT 物联网 yudao-module-iot - 已禁用]");
            }
            // IM 即时通讯
            if (isNotPresent("cn.iocoder.yudao.module.im.framework.web.config.ImWebConfiguration")) {
                System.out.println("[IM 即时通讯 yudao-module-im - 已禁用]");
            }
        });
    }

    private static boolean isNotPresent(String className) {
        return !ClassUtils.isPresent(className, ClassUtils.getDefaultClassLoader());
    }

}
