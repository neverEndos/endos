/*
 Yudao Database Transfer Tool

 Source Server Type    : MySQL

 Target Server Type    : PostgreSQL

 Date: 2026-09-20 13:27:14

 说明：本脚本面向 yudao-mini（仅启用 system / infra 模块）精简维护。
 已移除演示表及禁用业务模块（BPM、商城、CRM、ERP、AI、IoT、IM、
 公众号、支付、报表等）的菜单、字典与示例数据；表结构与核心运行数据保持可用。
 由 PostgreSQL 17 临时库经清理后导出生成。
*/

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11 (Debian 17.11-1.pgdg13+2)
-- Dumped by pg_dump version 17.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

DROP INDEX IF EXISTS public.idx_system_users_04;
DROP INDEX IF EXISTS public.idx_system_users_03;
DROP INDEX IF EXISTS public.idx_system_users_02;
DROP INDEX IF EXISTS public.idx_system_users_01;
DROP INDEX IF EXISTS public.idx_system_user_role_01;
DROP INDEX IF EXISTS public.idx_system_social_user_bind_01;
DROP INDEX IF EXISTS public.idx_system_social_user_02;
DROP INDEX IF EXISTS public.idx_system_social_user_01;
DROP INDEX IF EXISTS public.idx_system_sms_code_01;
DROP INDEX IF EXISTS public.idx_system_role_menu_01;
DROP INDEX IF EXISTS public.idx_system_operate_log_02;
DROP INDEX IF EXISTS public.idx_system_operate_log_01;
DROP INDEX IF EXISTS public.idx_system_oauth2_refresh_token_01;
DROP INDEX IF EXISTS public.idx_system_oauth2_code_01;
DROP INDEX IF EXISTS public.idx_system_oauth2_client_01;
DROP INDEX IF EXISTS public.idx_system_oauth2_approve_01;
DROP INDEX IF EXISTS public.idx_system_oauth2_access_token_02;
DROP INDEX IF EXISTS public.idx_system_oauth2_access_token_01;
DROP INDEX IF EXISTS public.idx_system_notify_message_01;
DROP INDEX IF EXISTS public.idx_system_login_log_02;
DROP INDEX IF EXISTS public.idx_system_login_log_01;
DROP INDEX IF EXISTS public.idx_infra_job_log_02;
DROP INDEX IF EXISTS public.idx_infra_job_log_01;
DROP INDEX IF EXISTS public.idx_infra_file_content_01;
DROP INDEX IF EXISTS public.idx_infra_config_01;
DROP INDEX IF EXISTS public.idx_infra_codegen_column_01;
DROP INDEX IF EXISTS public.idx_infra_api_error_log_01;
DROP INDEX IF EXISTS public.idx_infra_api_access_log_01;
ALTER TABLE IF EXISTS ONLY public.system_users DROP CONSTRAINT IF EXISTS pk_system_users;
ALTER TABLE IF EXISTS ONLY public.system_user_role DROP CONSTRAINT IF EXISTS pk_system_user_role;
ALTER TABLE IF EXISTS ONLY public.system_user_post DROP CONSTRAINT IF EXISTS pk_system_user_post;
ALTER TABLE IF EXISTS ONLY public.system_tenant_package DROP CONSTRAINT IF EXISTS pk_system_tenant_package;
ALTER TABLE IF EXISTS ONLY public.system_tenant DROP CONSTRAINT IF EXISTS pk_system_tenant;
ALTER TABLE IF EXISTS ONLY public.system_social_user_bind DROP CONSTRAINT IF EXISTS pk_system_social_user_bind;
ALTER TABLE IF EXISTS ONLY public.system_social_user DROP CONSTRAINT IF EXISTS pk_system_social_user;
ALTER TABLE IF EXISTS ONLY public.system_social_client DROP CONSTRAINT IF EXISTS pk_system_social_client;
ALTER TABLE IF EXISTS ONLY public.system_sms_template DROP CONSTRAINT IF EXISTS pk_system_sms_template;
ALTER TABLE IF EXISTS ONLY public.system_sms_log DROP CONSTRAINT IF EXISTS pk_system_sms_log;
ALTER TABLE IF EXISTS ONLY public.system_sms_code DROP CONSTRAINT IF EXISTS pk_system_sms_code;
ALTER TABLE IF EXISTS ONLY public.system_sms_channel DROP CONSTRAINT IF EXISTS pk_system_sms_channel;
ALTER TABLE IF EXISTS ONLY public.system_role_menu DROP CONSTRAINT IF EXISTS pk_system_role_menu;
ALTER TABLE IF EXISTS ONLY public.system_role DROP CONSTRAINT IF EXISTS pk_system_role;
ALTER TABLE IF EXISTS ONLY public.system_post DROP CONSTRAINT IF EXISTS pk_system_post;
ALTER TABLE IF EXISTS ONLY public.system_operate_log DROP CONSTRAINT IF EXISTS pk_system_operate_log;
ALTER TABLE IF EXISTS ONLY public.system_oauth2_refresh_token DROP CONSTRAINT IF EXISTS pk_system_oauth2_refresh_token;
ALTER TABLE IF EXISTS ONLY public.system_oauth2_code DROP CONSTRAINT IF EXISTS pk_system_oauth2_code;
ALTER TABLE IF EXISTS ONLY public.system_oauth2_client DROP CONSTRAINT IF EXISTS pk_system_oauth2_client;
ALTER TABLE IF EXISTS ONLY public.system_oauth2_approve DROP CONSTRAINT IF EXISTS pk_system_oauth2_approve;
ALTER TABLE IF EXISTS ONLY public.system_oauth2_access_token DROP CONSTRAINT IF EXISTS pk_system_oauth2_access_token;
ALTER TABLE IF EXISTS ONLY public.system_notify_template DROP CONSTRAINT IF EXISTS pk_system_notify_template;
ALTER TABLE IF EXISTS ONLY public.system_notify_message DROP CONSTRAINT IF EXISTS pk_system_notify_message;
ALTER TABLE IF EXISTS ONLY public.system_notice DROP CONSTRAINT IF EXISTS pk_system_notice;
ALTER TABLE IF EXISTS ONLY public.system_menu DROP CONSTRAINT IF EXISTS pk_system_menu;
ALTER TABLE IF EXISTS ONLY public.system_mail_template DROP CONSTRAINT IF EXISTS pk_system_mail_template;
ALTER TABLE IF EXISTS ONLY public.system_mail_log DROP CONSTRAINT IF EXISTS pk_system_mail_log;
ALTER TABLE IF EXISTS ONLY public.system_mail_account DROP CONSTRAINT IF EXISTS pk_system_mail_account;
ALTER TABLE IF EXISTS ONLY public.system_login_log DROP CONSTRAINT IF EXISTS pk_system_login_log;
ALTER TABLE IF EXISTS ONLY public.system_dict_type DROP CONSTRAINT IF EXISTS pk_system_dict_type;
ALTER TABLE IF EXISTS ONLY public.system_dict_data DROP CONSTRAINT IF EXISTS pk_system_dict_data;
ALTER TABLE IF EXISTS ONLY public.system_dept DROP CONSTRAINT IF EXISTS pk_system_dept;
ALTER TABLE IF EXISTS ONLY public.infra_job_log DROP CONSTRAINT IF EXISTS pk_infra_job_log;
ALTER TABLE IF EXISTS ONLY public.infra_job DROP CONSTRAINT IF EXISTS pk_infra_job;
ALTER TABLE IF EXISTS ONLY public.infra_file_content DROP CONSTRAINT IF EXISTS pk_infra_file_content;
ALTER TABLE IF EXISTS ONLY public.infra_file_config DROP CONSTRAINT IF EXISTS pk_infra_file_config;
ALTER TABLE IF EXISTS ONLY public.infra_file DROP CONSTRAINT IF EXISTS pk_infra_file;
ALTER TABLE IF EXISTS ONLY public.infra_data_source_config DROP CONSTRAINT IF EXISTS pk_infra_data_source_config;
ALTER TABLE IF EXISTS ONLY public.infra_config DROP CONSTRAINT IF EXISTS pk_infra_config;
ALTER TABLE IF EXISTS ONLY public.infra_codegen_table DROP CONSTRAINT IF EXISTS pk_infra_codegen_table;
ALTER TABLE IF EXISTS ONLY public.infra_codegen_column DROP CONSTRAINT IF EXISTS pk_infra_codegen_column;
ALTER TABLE IF EXISTS ONLY public.infra_api_error_log DROP CONSTRAINT IF EXISTS pk_infra_api_error_log;
ALTER TABLE IF EXISTS ONLY public.infra_api_access_log DROP CONSTRAINT IF EXISTS pk_infra_api_access_log;
DROP SEQUENCE IF EXISTS public.system_users_seq;
DROP TABLE IF EXISTS public.system_users;
DROP SEQUENCE IF EXISTS public.system_user_role_seq;
DROP TABLE IF EXISTS public.system_user_role;
DROP SEQUENCE IF EXISTS public.system_user_post_seq;
DROP TABLE IF EXISTS public.system_user_post;
DROP SEQUENCE IF EXISTS public.system_tenant_seq;
DROP SEQUENCE IF EXISTS public.system_tenant_package_seq;
DROP TABLE IF EXISTS public.system_tenant_package;
DROP TABLE IF EXISTS public.system_tenant;
DROP SEQUENCE IF EXISTS public.system_social_user_seq;
DROP SEQUENCE IF EXISTS public.system_social_user_bind_seq;
DROP TABLE IF EXISTS public.system_social_user_bind;
DROP TABLE IF EXISTS public.system_social_user;
DROP SEQUENCE IF EXISTS public.system_social_client_seq;
DROP TABLE IF EXISTS public.system_social_client;
DROP SEQUENCE IF EXISTS public.system_sms_template_seq;
DROP TABLE IF EXISTS public.system_sms_template;
DROP SEQUENCE IF EXISTS public.system_sms_log_seq;
DROP TABLE IF EXISTS public.system_sms_log;
DROP SEQUENCE IF EXISTS public.system_sms_code_seq;
DROP TABLE IF EXISTS public.system_sms_code;
DROP SEQUENCE IF EXISTS public.system_sms_channel_seq;
DROP TABLE IF EXISTS public.system_sms_channel;
DROP SEQUENCE IF EXISTS public.system_role_seq;
DROP SEQUENCE IF EXISTS public.system_role_menu_seq;
DROP TABLE IF EXISTS public.system_role_menu;
DROP TABLE IF EXISTS public.system_role;
DROP SEQUENCE IF EXISTS public.system_post_seq;
DROP TABLE IF EXISTS public.system_post;
DROP SEQUENCE IF EXISTS public.system_operate_log_seq;
DROP TABLE IF EXISTS public.system_operate_log;
DROP SEQUENCE IF EXISTS public.system_oauth2_refresh_token_seq;
DROP TABLE IF EXISTS public.system_oauth2_refresh_token;
DROP SEQUENCE IF EXISTS public.system_oauth2_code_seq;
DROP TABLE IF EXISTS public.system_oauth2_code;
DROP SEQUENCE IF EXISTS public.system_oauth2_client_seq;
DROP TABLE IF EXISTS public.system_oauth2_client;
DROP SEQUENCE IF EXISTS public.system_oauth2_approve_seq;
DROP TABLE IF EXISTS public.system_oauth2_approve;
DROP SEQUENCE IF EXISTS public.system_oauth2_access_token_seq;
DROP TABLE IF EXISTS public.system_oauth2_access_token;
DROP SEQUENCE IF EXISTS public.system_notify_template_seq;
DROP TABLE IF EXISTS public.system_notify_template;
DROP SEQUENCE IF EXISTS public.system_notify_message_seq;
DROP TABLE IF EXISTS public.system_notify_message;
DROP SEQUENCE IF EXISTS public.system_notice_seq;
DROP TABLE IF EXISTS public.system_notice;
DROP SEQUENCE IF EXISTS public.system_menu_seq;
DROP TABLE IF EXISTS public.system_menu;
DROP SEQUENCE IF EXISTS public.system_mail_template_seq;
DROP TABLE IF EXISTS public.system_mail_template;
DROP SEQUENCE IF EXISTS public.system_mail_log_seq;
DROP TABLE IF EXISTS public.system_mail_log;
DROP SEQUENCE IF EXISTS public.system_mail_account_seq;
DROP TABLE IF EXISTS public.system_mail_account;
DROP SEQUENCE IF EXISTS public.system_login_log_seq;
DROP TABLE IF EXISTS public.system_login_log;
DROP SEQUENCE IF EXISTS public.system_dict_type_seq;
DROP TABLE IF EXISTS public.system_dict_type;
DROP SEQUENCE IF EXISTS public.system_dict_data_seq;
DROP TABLE IF EXISTS public.system_dict_data;
DROP SEQUENCE IF EXISTS public.system_dept_seq;
DROP TABLE IF EXISTS public.system_dept;
DROP SEQUENCE IF EXISTS public.infra_job_seq;
DROP SEQUENCE IF EXISTS public.infra_job_log_seq;
DROP TABLE IF EXISTS public.infra_job_log;
DROP TABLE IF EXISTS public.infra_job;
DROP SEQUENCE IF EXISTS public.infra_file_seq;
DROP SEQUENCE IF EXISTS public.infra_file_content_seq;
DROP TABLE IF EXISTS public.infra_file_content;
DROP SEQUENCE IF EXISTS public.infra_file_config_seq;
DROP TABLE IF EXISTS public.infra_file_config;
DROP TABLE IF EXISTS public.infra_file;
DROP SEQUENCE IF EXISTS public.infra_data_source_config_seq;
DROP TABLE IF EXISTS public.infra_data_source_config;
DROP SEQUENCE IF EXISTS public.infra_config_seq;
DROP TABLE IF EXISTS public.infra_config;
DROP SEQUENCE IF EXISTS public.infra_codegen_table_seq;
DROP TABLE IF EXISTS public.infra_codegen_table;
DROP SEQUENCE IF EXISTS public.infra_codegen_column_seq;
DROP TABLE IF EXISTS public.infra_codegen_column;
DROP SEQUENCE IF EXISTS public.infra_api_error_log_seq;
DROP TABLE IF EXISTS public.infra_api_error_log;
DROP SEQUENCE IF EXISTS public.infra_api_access_log_seq;
DROP TABLE IF EXISTS public.infra_api_access_log;
DROP TABLE IF EXISTS public.dual;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: dual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dual (
    id smallint
);


--
-- Name: TABLE dual; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.dual IS '数据库连接的表';


--
-- Name: infra_api_access_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_api_access_log (
    id bigint NOT NULL,
    trace_id character varying(64) DEFAULT ''::character varying NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    application_name character varying(50) NOT NULL,
    request_method character varying(16) DEFAULT ''::character varying NOT NULL,
    request_url character varying(255) DEFAULT ''::character varying NOT NULL,
    request_params text,
    response_body text,
    user_ip character varying(50) NOT NULL,
    user_agent character varying(512) NOT NULL,
    operate_module character varying(50) DEFAULT NULL::character varying,
    operate_name character varying(50) DEFAULT NULL::character varying,
    operate_type smallint DEFAULT 0,
    begin_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone NOT NULL,
    duration integer NOT NULL,
    result_code integer DEFAULT 0 NOT NULL,
    result_msg character varying(512) DEFAULT ''::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_api_access_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_api_access_log IS 'API 访问日志表';


--
-- Name: COLUMN infra_api_access_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.id IS '日志主键';


--
-- Name: COLUMN infra_api_access_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN infra_api_access_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_id IS '用户编号';


--
-- Name: COLUMN infra_api_access_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_type IS '用户类型';


--
-- Name: COLUMN infra_api_access_log.application_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.application_name IS '应用名';


--
-- Name: COLUMN infra_api_access_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.request_method IS '请求方法名';


--
-- Name: COLUMN infra_api_access_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.request_url IS '请求地址';


--
-- Name: COLUMN infra_api_access_log.request_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.request_params IS '请求参数';


--
-- Name: COLUMN infra_api_access_log.response_body; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.response_body IS '响应结果';


--
-- Name: COLUMN infra_api_access_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_ip IS '用户 IP';


--
-- Name: COLUMN infra_api_access_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN infra_api_access_log.operate_module; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.operate_module IS '操作模块';


--
-- Name: COLUMN infra_api_access_log.operate_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.operate_name IS '操作名';


--
-- Name: COLUMN infra_api_access_log.operate_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.operate_type IS '操作分类';


--
-- Name: COLUMN infra_api_access_log.begin_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.begin_time IS '开始请求时间';


--
-- Name: COLUMN infra_api_access_log.end_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.end_time IS '结束请求时间';


--
-- Name: COLUMN infra_api_access_log.duration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.duration IS '执行时长';


--
-- Name: COLUMN infra_api_access_log.result_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.result_code IS '结果码';


--
-- Name: COLUMN infra_api_access_log.result_msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.result_msg IS '结果提示';


--
-- Name: COLUMN infra_api_access_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.creator IS '创建者';


--
-- Name: COLUMN infra_api_access_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.create_time IS '创建时间';


--
-- Name: COLUMN infra_api_access_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.updater IS '更新者';


--
-- Name: COLUMN infra_api_access_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.update_time IS '更新时间';


--
-- Name: COLUMN infra_api_access_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.deleted IS '是否删除';


--
-- Name: COLUMN infra_api_access_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.tenant_id IS '租户编号';


--
-- Name: infra_api_access_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_api_access_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_api_error_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_api_error_log (
    id bigint NOT NULL,
    trace_id character varying(64) NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    application_name character varying(50) NOT NULL,
    request_method character varying(16) NOT NULL,
    request_url character varying(255) NOT NULL,
    request_params character varying(8000) NOT NULL,
    user_ip character varying(50) NOT NULL,
    user_agent character varying(512) NOT NULL,
    exception_time timestamp without time zone NOT NULL,
    exception_name character varying(128) DEFAULT ''::character varying NOT NULL,
    exception_message text NOT NULL,
    exception_root_cause_message text NOT NULL,
    exception_stack_trace text NOT NULL,
    exception_class_name character varying(512) NOT NULL,
    exception_file_name character varying(512) NOT NULL,
    exception_method_name character varying(512) NOT NULL,
    exception_line_number integer NOT NULL,
    process_status smallint NOT NULL,
    process_time timestamp without time zone,
    process_user_id integer DEFAULT 0,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_api_error_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_api_error_log IS '系统异常日志';


--
-- Name: COLUMN infra_api_error_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.id IS '编号';


--
-- Name: COLUMN infra_api_error_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN infra_api_error_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_id IS '用户编号';


--
-- Name: COLUMN infra_api_error_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_type IS '用户类型';


--
-- Name: COLUMN infra_api_error_log.application_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.application_name IS '应用名';


--
-- Name: COLUMN infra_api_error_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.request_method IS '请求方法名';


--
-- Name: COLUMN infra_api_error_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.request_url IS '请求地址';


--
-- Name: COLUMN infra_api_error_log.request_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.request_params IS '请求参数';


--
-- Name: COLUMN infra_api_error_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_ip IS '用户 IP';


--
-- Name: COLUMN infra_api_error_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN infra_api_error_log.exception_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_time IS '异常发生时间';


--
-- Name: COLUMN infra_api_error_log.exception_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_name IS '异常名';


--
-- Name: COLUMN infra_api_error_log.exception_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_message IS '异常导致的消息';


--
-- Name: COLUMN infra_api_error_log.exception_root_cause_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_root_cause_message IS '异常导致的根消息';


--
-- Name: COLUMN infra_api_error_log.exception_stack_trace; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_stack_trace IS '异常的栈轨迹';


--
-- Name: COLUMN infra_api_error_log.exception_class_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_class_name IS '异常发生的类全名';


--
-- Name: COLUMN infra_api_error_log.exception_file_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_file_name IS '异常发生的类文件';


--
-- Name: COLUMN infra_api_error_log.exception_method_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_method_name IS '异常发生的方法名';


--
-- Name: COLUMN infra_api_error_log.exception_line_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_line_number IS '异常发生的方法所在行';


--
-- Name: COLUMN infra_api_error_log.process_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.process_status IS '处理状态';


--
-- Name: COLUMN infra_api_error_log.process_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.process_time IS '处理时间';


--
-- Name: COLUMN infra_api_error_log.process_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.process_user_id IS '处理用户编号';


--
-- Name: COLUMN infra_api_error_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.creator IS '创建者';


--
-- Name: COLUMN infra_api_error_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.create_time IS '创建时间';


--
-- Name: COLUMN infra_api_error_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.updater IS '更新者';


--
-- Name: COLUMN infra_api_error_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.update_time IS '更新时间';


--
-- Name: COLUMN infra_api_error_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.deleted IS '是否删除';


--
-- Name: COLUMN infra_api_error_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.tenant_id IS '租户编号';


--
-- Name: infra_api_error_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_api_error_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_codegen_column; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_codegen_column (
    id bigint NOT NULL,
    table_id bigint NOT NULL,
    column_name character varying(200) NOT NULL,
    data_type character varying(100) NOT NULL,
    column_comment character varying(500) NOT NULL,
    nullable boolean NOT NULL,
    primary_key boolean NOT NULL,
    ordinal_position integer NOT NULL,
    java_type character varying(32) NOT NULL,
    java_field character varying(64) NOT NULL,
    dict_type character varying(200) DEFAULT ''::character varying,
    example character varying(64) DEFAULT NULL::character varying,
    create_operation boolean NOT NULL,
    update_operation boolean NOT NULL,
    list_operation boolean NOT NULL,
    list_operation_condition character varying(32) DEFAULT '='::character varying NOT NULL,
    list_operation_result boolean NOT NULL,
    html_type character varying(32) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_codegen_column; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_codegen_column IS '代码生成表字段定义';


--
-- Name: COLUMN infra_codegen_column.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.id IS '编号';


--
-- Name: COLUMN infra_codegen_column.table_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.table_id IS '表编号';


--
-- Name: COLUMN infra_codegen_column.column_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.column_name IS '字段名';


--
-- Name: COLUMN infra_codegen_column.data_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.data_type IS '字段类型';


--
-- Name: COLUMN infra_codegen_column.column_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.column_comment IS '字段描述';


--
-- Name: COLUMN infra_codegen_column.nullable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.nullable IS '是否允许为空';


--
-- Name: COLUMN infra_codegen_column.primary_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.primary_key IS '是否主键';


--
-- Name: COLUMN infra_codegen_column.ordinal_position; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.ordinal_position IS '排序';


--
-- Name: COLUMN infra_codegen_column.java_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.java_type IS 'Java 属性类型';


--
-- Name: COLUMN infra_codegen_column.java_field; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.java_field IS 'Java 属性名';


--
-- Name: COLUMN infra_codegen_column.dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.dict_type IS '字典类型';


--
-- Name: COLUMN infra_codegen_column.example; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.example IS '数据示例';


--
-- Name: COLUMN infra_codegen_column.create_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.create_operation IS '是否为 Create 创建操作的字段';


--
-- Name: COLUMN infra_codegen_column.update_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.update_operation IS '是否为 Update 更新操作的字段';


--
-- Name: COLUMN infra_codegen_column.list_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.list_operation IS '是否为 List 查询操作的字段';


--
-- Name: COLUMN infra_codegen_column.list_operation_condition; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.list_operation_condition IS 'List 查询操作的条件类型';


--
-- Name: COLUMN infra_codegen_column.list_operation_result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.list_operation_result IS '是否为 List 查询操作的返回字段';


--
-- Name: COLUMN infra_codegen_column.html_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.html_type IS '显示类型';


--
-- Name: COLUMN infra_codegen_column.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.creator IS '创建者';


--
-- Name: COLUMN infra_codegen_column.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.create_time IS '创建时间';


--
-- Name: COLUMN infra_codegen_column.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.updater IS '更新者';


--
-- Name: COLUMN infra_codegen_column.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.update_time IS '更新时间';


--
-- Name: COLUMN infra_codegen_column.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.deleted IS '是否删除';


--
-- Name: infra_codegen_column_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_codegen_column_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_codegen_table; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_codegen_table (
    id bigint NOT NULL,
    data_source_config_id bigint NOT NULL,
    scene smallint DEFAULT 1 NOT NULL,
    table_name character varying(200) DEFAULT ''::character varying NOT NULL,
    table_comment character varying(500) DEFAULT ''::character varying NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    module_name character varying(30) NOT NULL,
    business_name character varying(30) NOT NULL,
    class_name character varying(100) DEFAULT ''::character varying NOT NULL,
    class_comment character varying(50) NOT NULL,
    author character varying(50) NOT NULL,
    template_type smallint DEFAULT 1 NOT NULL,
    front_type smallint NOT NULL,
    parent_menu_id bigint,
    master_table_id bigint,
    sub_join_column_id bigint,
    sub_join_many boolean,
    tree_parent_column_id bigint,
    tree_name_column_id bigint,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_codegen_table; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_codegen_table IS '代码生成表定义';


--
-- Name: COLUMN infra_codegen_table.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.id IS '编号';


--
-- Name: COLUMN infra_codegen_table.data_source_config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.data_source_config_id IS '数据源配置的编号';


--
-- Name: COLUMN infra_codegen_table.scene; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.scene IS '生成场景';


--
-- Name: COLUMN infra_codegen_table.table_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.table_name IS '表名称';


--
-- Name: COLUMN infra_codegen_table.table_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.table_comment IS '表描述';


--
-- Name: COLUMN infra_codegen_table.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.remark IS '备注';


--
-- Name: COLUMN infra_codegen_table.module_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.module_name IS '模块名';


--
-- Name: COLUMN infra_codegen_table.business_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.business_name IS '业务名';


--
-- Name: COLUMN infra_codegen_table.class_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.class_name IS '类名称';


--
-- Name: COLUMN infra_codegen_table.class_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.class_comment IS '类描述';


--
-- Name: COLUMN infra_codegen_table.author; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.author IS '作者';


--
-- Name: COLUMN infra_codegen_table.template_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.template_type IS '模板类型';


--
-- Name: COLUMN infra_codegen_table.front_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.front_type IS '前端类型';


--
-- Name: COLUMN infra_codegen_table.parent_menu_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.parent_menu_id IS '父菜单编号';


--
-- Name: COLUMN infra_codegen_table.master_table_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.master_table_id IS '主表的编号';


--
-- Name: COLUMN infra_codegen_table.sub_join_column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.sub_join_column_id IS '子表关联主表的字段编号';


--
-- Name: COLUMN infra_codegen_table.sub_join_many; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.sub_join_many IS '主表与子表是否一对多';


--
-- Name: COLUMN infra_codegen_table.tree_parent_column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.tree_parent_column_id IS '树表的父字段编号';


--
-- Name: COLUMN infra_codegen_table.tree_name_column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.tree_name_column_id IS '树表的名字字段编号';


--
-- Name: COLUMN infra_codegen_table.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.creator IS '创建者';


--
-- Name: COLUMN infra_codegen_table.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.create_time IS '创建时间';


--
-- Name: COLUMN infra_codegen_table.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.updater IS '更新者';


--
-- Name: COLUMN infra_codegen_table.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.update_time IS '更新时间';


--
-- Name: COLUMN infra_codegen_table.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.deleted IS '是否删除';


--
-- Name: infra_codegen_table_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_codegen_table_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_config (
    id bigint NOT NULL,
    category character varying(50) NOT NULL,
    type smallint NOT NULL,
    name character varying(100) DEFAULT ''::character varying NOT NULL,
    config_key character varying(100) DEFAULT ''::character varying NOT NULL,
    value character varying(500) DEFAULT ''::character varying NOT NULL,
    visible boolean NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_config IS '参数配置表';


--
-- Name: COLUMN infra_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.id IS '参数主键';


--
-- Name: COLUMN infra_config.category; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.category IS '参数分组';


--
-- Name: COLUMN infra_config.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.type IS '参数类型';


--
-- Name: COLUMN infra_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.name IS '参数名称';


--
-- Name: COLUMN infra_config.config_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.config_key IS '参数键名';


--
-- Name: COLUMN infra_config.value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.value IS '参数键值';


--
-- Name: COLUMN infra_config.visible; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.visible IS '是否可见';


--
-- Name: COLUMN infra_config.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.remark IS '备注';


--
-- Name: COLUMN infra_config.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.creator IS '创建者';


--
-- Name: COLUMN infra_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.create_time IS '创建时间';


--
-- Name: COLUMN infra_config.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.updater IS '更新者';


--
-- Name: COLUMN infra_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.update_time IS '更新时间';


--
-- Name: COLUMN infra_config.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.deleted IS '是否删除';


--
-- Name: infra_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_config_seq
    START WITH 14
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_data_source_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_data_source_config (
    id bigint NOT NULL,
    name character varying(100) DEFAULT ''::character varying NOT NULL,
    url character varying(1024) NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) DEFAULT ''::character varying NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_data_source_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_data_source_config IS '数据源配置表';


--
-- Name: COLUMN infra_data_source_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.id IS '主键编号';


--
-- Name: COLUMN infra_data_source_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.name IS '参数名称';


--
-- Name: COLUMN infra_data_source_config.url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.url IS '数据源连接';


--
-- Name: COLUMN infra_data_source_config.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.username IS '用户名';


--
-- Name: COLUMN infra_data_source_config.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.password IS '密码';


--
-- Name: COLUMN infra_data_source_config.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.creator IS '创建者';


--
-- Name: COLUMN infra_data_source_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.create_time IS '创建时间';


--
-- Name: COLUMN infra_data_source_config.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.updater IS '更新者';


--
-- Name: COLUMN infra_data_source_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.update_time IS '更新时间';


--
-- Name: COLUMN infra_data_source_config.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.deleted IS '是否删除';


--
-- Name: infra_data_source_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_data_source_config_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_file (
    id bigint NOT NULL,
    config_id bigint,
    name character varying(256) DEFAULT NULL::character varying,
    path character varying(512) NOT NULL,
    url character varying(1024) NOT NULL,
    type character varying(128) DEFAULT NULL::character varying,
    size integer NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_file IS '文件表';


--
-- Name: COLUMN infra_file.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.id IS '文件编号';


--
-- Name: COLUMN infra_file.config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.config_id IS '配置编号';


--
-- Name: COLUMN infra_file.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.name IS '文件名';


--
-- Name: COLUMN infra_file.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.path IS '文件路径';


--
-- Name: COLUMN infra_file.url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.url IS '文件 URL';


--
-- Name: COLUMN infra_file.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.type IS '文件类型';


--
-- Name: COLUMN infra_file.size; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.size IS '文件大小';


--
-- Name: COLUMN infra_file.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.creator IS '创建者';


--
-- Name: COLUMN infra_file.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.create_time IS '创建时间';


--
-- Name: COLUMN infra_file.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.updater IS '更新者';


--
-- Name: COLUMN infra_file.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.update_time IS '更新时间';


--
-- Name: COLUMN infra_file.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.deleted IS '是否删除';


--
-- Name: infra_file_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_file_config (
    id bigint NOT NULL,
    name character varying(63) NOT NULL,
    storage smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    master boolean NOT NULL,
    config character varying(4096) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_file_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_file_config IS '文件配置表';


--
-- Name: COLUMN infra_file_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.id IS '编号';


--
-- Name: COLUMN infra_file_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.name IS '配置名';


--
-- Name: COLUMN infra_file_config.storage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.storage IS '存储器';


--
-- Name: COLUMN infra_file_config.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.remark IS '备注';


--
-- Name: COLUMN infra_file_config.master; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.master IS '是否为主配置';


--
-- Name: COLUMN infra_file_config.config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.config IS '存储配置';


--
-- Name: COLUMN infra_file_config.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.creator IS '创建者';


--
-- Name: COLUMN infra_file_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.create_time IS '创建时间';


--
-- Name: COLUMN infra_file_config.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.updater IS '更新者';


--
-- Name: COLUMN infra_file_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.update_time IS '更新时间';


--
-- Name: COLUMN infra_file_config.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.deleted IS '是否删除';


--
-- Name: infra_file_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_file_config_seq
    START WITH 36
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_file_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_file_content (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    path character varying(512) NOT NULL,
    content bytea NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_file_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_file_content IS '文件表';


--
-- Name: COLUMN infra_file_content.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.id IS '编号';


--
-- Name: COLUMN infra_file_content.config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.config_id IS '配置编号';


--
-- Name: COLUMN infra_file_content.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.path IS '文件路径';


--
-- Name: COLUMN infra_file_content.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.content IS '文件内容';


--
-- Name: COLUMN infra_file_content.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.creator IS '创建者';


--
-- Name: COLUMN infra_file_content.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.create_time IS '创建时间';


--
-- Name: COLUMN infra_file_content.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.updater IS '更新者';


--
-- Name: COLUMN infra_file_content.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.update_time IS '更新时间';


--
-- Name: COLUMN infra_file_content.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.deleted IS '是否删除';


--
-- Name: infra_file_content_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_file_content_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_file_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_file_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_job (
    id bigint NOT NULL,
    name character varying(32) NOT NULL,
    status smallint NOT NULL,
    handler_name character varying(64) NOT NULL,
    handler_param character varying(255) DEFAULT NULL::character varying,
    cron_expression character varying(32) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    retry_interval integer DEFAULT 0 NOT NULL,
    monitor_timeout integer DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_job; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_job IS '定时任务表';


--
-- Name: COLUMN infra_job.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.id IS '任务编号';


--
-- Name: COLUMN infra_job.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.name IS '任务名称';


--
-- Name: COLUMN infra_job.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.status IS '任务状态';


--
-- Name: COLUMN infra_job.handler_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.handler_name IS '处理器的名字';


--
-- Name: COLUMN infra_job.handler_param; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.handler_param IS '处理器的参数';


--
-- Name: COLUMN infra_job.cron_expression; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.cron_expression IS 'CRON 表达式';


--
-- Name: COLUMN infra_job.retry_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.retry_count IS '重试次数';


--
-- Name: COLUMN infra_job.retry_interval; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.retry_interval IS '重试间隔';


--
-- Name: COLUMN infra_job.monitor_timeout; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.monitor_timeout IS '监控超时时间';


--
-- Name: COLUMN infra_job.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.creator IS '创建者';


--
-- Name: COLUMN infra_job.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.create_time IS '创建时间';


--
-- Name: COLUMN infra_job.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.updater IS '更新者';


--
-- Name: COLUMN infra_job.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.update_time IS '更新时间';


--
-- Name: COLUMN infra_job.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.deleted IS '是否删除';


--
-- Name: infra_job_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_job_log (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    handler_name character varying(64) NOT NULL,
    handler_param character varying(255) DEFAULT NULL::character varying,
    execute_index smallint DEFAULT 1 NOT NULL,
    begin_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone,
    duration integer,
    status smallint NOT NULL,
    result character varying(4000) DEFAULT ''::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_job_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_job_log IS '定时任务日志表';


--
-- Name: COLUMN infra_job_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.id IS '日志编号';


--
-- Name: COLUMN infra_job_log.job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.job_id IS '任务编号';


--
-- Name: COLUMN infra_job_log.handler_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.handler_name IS '处理器的名字';


--
-- Name: COLUMN infra_job_log.handler_param; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.handler_param IS '处理器的参数';


--
-- Name: COLUMN infra_job_log.execute_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.execute_index IS '第几次执行';


--
-- Name: COLUMN infra_job_log.begin_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.begin_time IS '开始执行时间';


--
-- Name: COLUMN infra_job_log.end_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.end_time IS '结束执行时间';


--
-- Name: COLUMN infra_job_log.duration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.duration IS '执行时长';


--
-- Name: COLUMN infra_job_log.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.status IS '任务状态';


--
-- Name: COLUMN infra_job_log.result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.result IS '结果数据';


--
-- Name: COLUMN infra_job_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.creator IS '创建者';


--
-- Name: COLUMN infra_job_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.create_time IS '创建时间';


--
-- Name: COLUMN infra_job_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.updater IS '更新者';


--
-- Name: COLUMN infra_job_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.update_time IS '更新时间';


--
-- Name: COLUMN infra_job_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.deleted IS '是否删除';


--
-- Name: infra_job_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_job_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_job_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_job_seq
    START WITH 12909
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dept; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dept (
    id bigint NOT NULL,
    name character varying(30) DEFAULT ''::character varying NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    leader_user_id bigint,
    phone character varying(11) DEFAULT NULL::character varying,
    email character varying(50) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_dept; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_dept IS '部门表';


--
-- Name: COLUMN system_dept.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.id IS '部门id';


--
-- Name: COLUMN system_dept.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.name IS '部门名称';


--
-- Name: COLUMN system_dept.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.parent_id IS '父部门id';


--
-- Name: COLUMN system_dept.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.sort IS '显示顺序';


--
-- Name: COLUMN system_dept.leader_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.leader_user_id IS '负责人';


--
-- Name: COLUMN system_dept.phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.phone IS '联系电话';


--
-- Name: COLUMN system_dept.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.email IS '邮箱';


--
-- Name: COLUMN system_dept.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.status IS '部门状态（0正常 1停用）';


--
-- Name: COLUMN system_dept.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.creator IS '创建者';


--
-- Name: COLUMN system_dept.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.create_time IS '创建时间';


--
-- Name: COLUMN system_dept.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.updater IS '更新者';


--
-- Name: COLUMN system_dept.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.update_time IS '更新时间';


--
-- Name: COLUMN system_dept.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.deleted IS '是否删除';


--
-- Name: COLUMN system_dept.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.tenant_id IS '租户编号';


--
-- Name: system_dept_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dept_seq
    START WITH 118
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dict_data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dict_data (
    id bigint NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    label character varying(100) DEFAULT ''::character varying NOT NULL,
    value character varying(100) DEFAULT ''::character varying NOT NULL,
    dict_type character varying(100) DEFAULT ''::character varying NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    color_type character varying(100) DEFAULT ''::character varying,
    css_class character varying(100) DEFAULT ''::character varying,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_dict_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_dict_data IS '字典数据表';


--
-- Name: COLUMN system_dict_data.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.id IS '字典编码';


--
-- Name: COLUMN system_dict_data.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.sort IS '字典排序';


--
-- Name: COLUMN system_dict_data.label; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.label IS '字典标签';


--
-- Name: COLUMN system_dict_data.value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.value IS '字典键值';


--
-- Name: COLUMN system_dict_data.dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.dict_type IS '字典类型';


--
-- Name: COLUMN system_dict_data.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.status IS '状态（0正常 1停用）';


--
-- Name: COLUMN system_dict_data.color_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.color_type IS '颜色类型';


--
-- Name: COLUMN system_dict_data.css_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.css_class IS 'css 样式';


--
-- Name: COLUMN system_dict_data.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.remark IS '备注';


--
-- Name: COLUMN system_dict_data.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.creator IS '创建者';


--
-- Name: COLUMN system_dict_data.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.create_time IS '创建时间';


--
-- Name: COLUMN system_dict_data.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.updater IS '更新者';


--
-- Name: COLUMN system_dict_data.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.update_time IS '更新时间';


--
-- Name: COLUMN system_dict_data.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.deleted IS '是否删除';


--
-- Name: system_dict_data_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dict_data_seq
    START WITH 1635
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dict_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dict_type (
    id bigint NOT NULL,
    name character varying(100) DEFAULT ''::character varying NOT NULL,
    type character varying(100) DEFAULT ''::character varying NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    deleted_time timestamp without time zone
);


--
-- Name: TABLE system_dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_dict_type IS '字典类型表';


--
-- Name: COLUMN system_dict_type.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.id IS '字典主键';


--
-- Name: COLUMN system_dict_type.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.name IS '字典名称';


--
-- Name: COLUMN system_dict_type.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.type IS '字典类型';


--
-- Name: COLUMN system_dict_type.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.status IS '状态（0正常 1停用）';


--
-- Name: COLUMN system_dict_type.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.remark IS '备注';


--
-- Name: COLUMN system_dict_type.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.creator IS '创建者';


--
-- Name: COLUMN system_dict_type.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.create_time IS '创建时间';


--
-- Name: COLUMN system_dict_type.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.updater IS '更新者';


--
-- Name: COLUMN system_dict_type.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.update_time IS '更新时间';


--
-- Name: COLUMN system_dict_type.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.deleted IS '是否删除';


--
-- Name: COLUMN system_dict_type.deleted_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.deleted_time IS '删除时间';


--
-- Name: system_dict_type_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dict_type_seq
    START WITH 353
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_login_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_login_log (
    id bigint NOT NULL,
    log_type bigint NOT NULL,
    trace_id character varying(64) DEFAULT ''::character varying NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    username character varying(50) DEFAULT ''::character varying NOT NULL,
    result smallint NOT NULL,
    user_ip character varying(50) NOT NULL,
    user_agent character varying(512) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_login_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_login_log IS '系统访问记录';


--
-- Name: COLUMN system_login_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.id IS '访问ID';


--
-- Name: COLUMN system_login_log.log_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.log_type IS '日志类型';


--
-- Name: COLUMN system_login_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN system_login_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_id IS '用户编号';


--
-- Name: COLUMN system_login_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_type IS '用户类型';


--
-- Name: COLUMN system_login_log.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.username IS '用户账号';


--
-- Name: COLUMN system_login_log.result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.result IS '登陆结果';


--
-- Name: COLUMN system_login_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_ip IS '用户 IP';


--
-- Name: COLUMN system_login_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN system_login_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.creator IS '创建者';


--
-- Name: COLUMN system_login_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.create_time IS '创建时间';


--
-- Name: COLUMN system_login_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.updater IS '更新者';


--
-- Name: COLUMN system_login_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.update_time IS '更新时间';


--
-- Name: COLUMN system_login_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.deleted IS '是否删除';


--
-- Name: COLUMN system_login_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.tenant_id IS '租户编号';


--
-- Name: system_login_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_login_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_mail_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mail_account (
    id bigint NOT NULL,
    mail character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    host character varying(255) NOT NULL,
    port integer NOT NULL,
    ssl_enable boolean DEFAULT false NOT NULL,
    starttls_enable boolean DEFAULT false NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_mail_account; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_mail_account IS '邮箱账号表';


--
-- Name: COLUMN system_mail_account.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.id IS '主键';


--
-- Name: COLUMN system_mail_account.mail; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.mail IS '邮箱';


--
-- Name: COLUMN system_mail_account.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.username IS '用户名';


--
-- Name: COLUMN system_mail_account.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.password IS '密码';


--
-- Name: COLUMN system_mail_account.host; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.host IS 'SMTP 服务器域名';


--
-- Name: COLUMN system_mail_account.port; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.port IS 'SMTP 服务器端口';


--
-- Name: COLUMN system_mail_account.ssl_enable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.ssl_enable IS '是否开启 SSL';


--
-- Name: COLUMN system_mail_account.starttls_enable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.starttls_enable IS '是否开启 STARTTLS';


--
-- Name: COLUMN system_mail_account.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.creator IS '创建者';


--
-- Name: COLUMN system_mail_account.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.create_time IS '创建时间';


--
-- Name: COLUMN system_mail_account.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.updater IS '更新者';


--
-- Name: COLUMN system_mail_account.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.update_time IS '更新时间';


--
-- Name: COLUMN system_mail_account.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.deleted IS '是否删除';


--
-- Name: system_mail_account_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_mail_account_seq
    START WITH 5
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_mail_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mail_log (
    id bigint NOT NULL,
    user_id bigint,
    user_type smallint,
    to_mails character varying(1024) NOT NULL,
    cc_mails character varying(1024) DEFAULT NULL::character varying,
    bcc_mails character varying(1024) DEFAULT NULL::character varying,
    account_id bigint NOT NULL,
    from_mail character varying(255) NOT NULL,
    template_id bigint NOT NULL,
    template_code character varying(63) NOT NULL,
    template_nickname character varying(255) DEFAULT NULL::character varying,
    template_title character varying(255) NOT NULL,
    template_content text NOT NULL,
    template_params character varying(255) NOT NULL,
    send_status smallint DEFAULT 0 NOT NULL,
    send_time timestamp without time zone,
    send_message_id character varying(255) DEFAULT NULL::character varying,
    send_exception character varying(4096) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_mail_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_mail_log IS '邮件日志表';


--
-- Name: COLUMN system_mail_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.id IS '编号';


--
-- Name: COLUMN system_mail_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.user_id IS '用户编号';


--
-- Name: COLUMN system_mail_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.user_type IS '用户类型';


--
-- Name: COLUMN system_mail_log.to_mails; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.to_mails IS '接收邮箱地址';


--
-- Name: COLUMN system_mail_log.cc_mails; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.cc_mails IS '抄送邮箱地址';


--
-- Name: COLUMN system_mail_log.bcc_mails; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.bcc_mails IS '密送邮箱地址';


--
-- Name: COLUMN system_mail_log.account_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.account_id IS '邮箱账号编号';


--
-- Name: COLUMN system_mail_log.from_mail; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.from_mail IS '发送邮箱地址';


--
-- Name: COLUMN system_mail_log.template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_id IS '模板编号';


--
-- Name: COLUMN system_mail_log.template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_code IS '模板编码';


--
-- Name: COLUMN system_mail_log.template_nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_nickname IS '模版发送人名称';


--
-- Name: COLUMN system_mail_log.template_title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_title IS '邮件标题';


--
-- Name: COLUMN system_mail_log.template_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_content IS '邮件内容';


--
-- Name: COLUMN system_mail_log.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_params IS '邮件参数';


--
-- Name: COLUMN system_mail_log.send_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_status IS '发送状态';


--
-- Name: COLUMN system_mail_log.send_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_time IS '发送时间';


--
-- Name: COLUMN system_mail_log.send_message_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_message_id IS '发送返回的消息 ID';


--
-- Name: COLUMN system_mail_log.send_exception; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_exception IS '发送异常';


--
-- Name: COLUMN system_mail_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.creator IS '创建者';


--
-- Name: COLUMN system_mail_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.create_time IS '创建时间';


--
-- Name: COLUMN system_mail_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.updater IS '更新者';


--
-- Name: COLUMN system_mail_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.update_time IS '更新时间';


--
-- Name: COLUMN system_mail_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.deleted IS '是否删除';


--
-- Name: system_mail_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_mail_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_mail_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mail_template (
    id bigint NOT NULL,
    name character varying(63) NOT NULL,
    code character varying(63) NOT NULL,
    account_id bigint NOT NULL,
    nickname character varying(255) DEFAULT NULL::character varying,
    title character varying(255) NOT NULL,
    content character varying(10240) NOT NULL,
    params character varying(255) NOT NULL,
    status smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_mail_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_mail_template IS '邮件模版表';


--
-- Name: COLUMN system_mail_template.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.id IS '编号';


--
-- Name: COLUMN system_mail_template.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.name IS '模板名称';


--
-- Name: COLUMN system_mail_template.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.code IS '模板编码';


--
-- Name: COLUMN system_mail_template.account_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.account_id IS '发送的邮箱账号编号';


--
-- Name: COLUMN system_mail_template.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.nickname IS '发送人名称';


--
-- Name: COLUMN system_mail_template.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.title IS '模板标题';


--
-- Name: COLUMN system_mail_template.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.content IS '模板内容';


--
-- Name: COLUMN system_mail_template.params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.params IS '参数数组';


--
-- Name: COLUMN system_mail_template.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.status IS '开启状态';


--
-- Name: COLUMN system_mail_template.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.remark IS '备注';


--
-- Name: COLUMN system_mail_template.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.creator IS '创建者';


--
-- Name: COLUMN system_mail_template.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.create_time IS '创建时间';


--
-- Name: COLUMN system_mail_template.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.updater IS '更新者';


--
-- Name: COLUMN system_mail_template.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.update_time IS '更新时间';


--
-- Name: COLUMN system_mail_template.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.deleted IS '是否删除';


--
-- Name: system_mail_template_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_mail_template_seq
    START WITH 16
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_menu (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    permission character varying(100) DEFAULT ''::character varying NOT NULL,
    type smallint NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    path character varying(200) DEFAULT ''::character varying,
    icon character varying(100) DEFAULT '#'::character varying,
    component character varying(255) DEFAULT NULL::character varying,
    component_name character varying(255) DEFAULT NULL::character varying,
    status smallint DEFAULT 0 NOT NULL,
    visible boolean DEFAULT true NOT NULL,
    keep_alive boolean DEFAULT true NOT NULL,
    always_show boolean DEFAULT true NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_menu IS '菜单权限表';


--
-- Name: COLUMN system_menu.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.id IS '菜单ID';


--
-- Name: COLUMN system_menu.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.name IS '菜单名称';


--
-- Name: COLUMN system_menu.permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.permission IS '权限标识';


--
-- Name: COLUMN system_menu.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.type IS '菜单类型';


--
-- Name: COLUMN system_menu.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.sort IS '显示顺序';


--
-- Name: COLUMN system_menu.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.parent_id IS '父菜单ID';


--
-- Name: COLUMN system_menu.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.path IS '路由地址';


--
-- Name: COLUMN system_menu.icon; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.icon IS '菜单图标';


--
-- Name: COLUMN system_menu.component; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.component IS '组件路径';


--
-- Name: COLUMN system_menu.component_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.component_name IS '组件名';


--
-- Name: COLUMN system_menu.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.status IS '菜单状态';


--
-- Name: COLUMN system_menu.visible; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.visible IS '是否可见';


--
-- Name: COLUMN system_menu.keep_alive; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.keep_alive IS '是否缓存';


--
-- Name: COLUMN system_menu.always_show; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.always_show IS '是否总是显示';


--
-- Name: COLUMN system_menu.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.creator IS '创建者';


--
-- Name: COLUMN system_menu.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.create_time IS '创建时间';


--
-- Name: COLUMN system_menu.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.updater IS '更新者';


--
-- Name: COLUMN system_menu.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.update_time IS '更新时间';


--
-- Name: COLUMN system_menu.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.deleted IS '是否删除';


--
-- Name: system_menu_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_menu_seq
    START WITH 12940
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notice (
    id bigint NOT NULL,
    title character varying(50) NOT NULL,
    content text NOT NULL,
    type smallint NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_notice; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_notice IS '通知公告表';


--
-- Name: COLUMN system_notice.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.id IS '公告ID';


--
-- Name: COLUMN system_notice.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.title IS '公告标题';


--
-- Name: COLUMN system_notice.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.content IS '公告内容';


--
-- Name: COLUMN system_notice.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.type IS '公告类型（1通知 2公告）';


--
-- Name: COLUMN system_notice.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.status IS '公告状态（0正常 1关闭）';


--
-- Name: COLUMN system_notice.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.creator IS '创建者';


--
-- Name: COLUMN system_notice.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.create_time IS '创建时间';


--
-- Name: COLUMN system_notice.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.updater IS '更新者';


--
-- Name: COLUMN system_notice.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.update_time IS '更新时间';


--
-- Name: COLUMN system_notice.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.deleted IS '是否删除';


--
-- Name: COLUMN system_notice.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.tenant_id IS '租户编号';


--
-- Name: system_notice_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notice_seq
    START WITH 5
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notify_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notify_message (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    template_id bigint NOT NULL,
    template_code character varying(64) NOT NULL,
    template_nickname character varying(63) NOT NULL,
    template_content character varying(1024) NOT NULL,
    template_type integer NOT NULL,
    template_params character varying(255) NOT NULL,
    read_status boolean NOT NULL,
    read_time timestamp without time zone,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_notify_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_notify_message IS '站内信消息表';


--
-- Name: COLUMN system_notify_message.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.id IS '用户ID';


--
-- Name: COLUMN system_notify_message.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.user_id IS '用户id';


--
-- Name: COLUMN system_notify_message.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.user_type IS '用户类型';


--
-- Name: COLUMN system_notify_message.template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_id IS '模版编号';


--
-- Name: COLUMN system_notify_message.template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_code IS '模板编码';


--
-- Name: COLUMN system_notify_message.template_nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_nickname IS '模版发送人名称';


--
-- Name: COLUMN system_notify_message.template_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_content IS '模版内容';


--
-- Name: COLUMN system_notify_message.template_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_type IS '模版类型';


--
-- Name: COLUMN system_notify_message.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_params IS '模版参数';


--
-- Name: COLUMN system_notify_message.read_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.read_status IS '是否已读';


--
-- Name: COLUMN system_notify_message.read_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.read_time IS '阅读时间';


--
-- Name: COLUMN system_notify_message.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.creator IS '创建者';


--
-- Name: COLUMN system_notify_message.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.create_time IS '创建时间';


--
-- Name: COLUMN system_notify_message.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.updater IS '更新者';


--
-- Name: COLUMN system_notify_message.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.update_time IS '更新时间';


--
-- Name: COLUMN system_notify_message.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.deleted IS '是否删除';


--
-- Name: COLUMN system_notify_message.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.tenant_id IS '租户编号';


--
-- Name: system_notify_message_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notify_message_seq
    START WITH 97
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notify_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notify_template (
    id bigint NOT NULL,
    name character varying(63) NOT NULL,
    code character varying(64) NOT NULL,
    nickname character varying(255) NOT NULL,
    content character varying(1024) NOT NULL,
    type smallint NOT NULL,
    params character varying(255) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_notify_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_notify_template IS '站内信模板表';


--
-- Name: COLUMN system_notify_template.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.id IS '主键';


--
-- Name: COLUMN system_notify_template.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.name IS '模板名称';


--
-- Name: COLUMN system_notify_template.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.code IS '模版编码';


--
-- Name: COLUMN system_notify_template.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.nickname IS '发送人名称';


--
-- Name: COLUMN system_notify_template.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.content IS '模版内容';


--
-- Name: COLUMN system_notify_template.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.type IS '类型';


--
-- Name: COLUMN system_notify_template.params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.params IS '参数数组';


--
-- Name: COLUMN system_notify_template.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.status IS '状态';


--
-- Name: COLUMN system_notify_template.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.remark IS '备注';


--
-- Name: COLUMN system_notify_template.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.creator IS '创建者';


--
-- Name: COLUMN system_notify_template.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.create_time IS '创建时间';


--
-- Name: COLUMN system_notify_template.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.updater IS '更新者';


--
-- Name: COLUMN system_notify_template.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.update_time IS '更新时间';


--
-- Name: COLUMN system_notify_template.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.deleted IS '是否删除';


--
-- Name: system_notify_template_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notify_template_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_access_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_access_token (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    user_info character varying(512) NOT NULL,
    access_token character varying(255) NOT NULL,
    refresh_token character varying(32) NOT NULL,
    client_id character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT NULL::character varying,
    expires_time timestamp without time zone NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_access_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_access_token IS 'OAuth2 访问令牌';


--
-- Name: COLUMN system_oauth2_access_token.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.id IS '编号';


--
-- Name: COLUMN system_oauth2_access_token.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_access_token.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_access_token.user_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.user_info IS '用户信息';


--
-- Name: COLUMN system_oauth2_access_token.access_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.access_token IS '访问令牌';


--
-- Name: COLUMN system_oauth2_access_token.refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.refresh_token IS '刷新令牌';


--
-- Name: COLUMN system_oauth2_access_token.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_access_token.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_access_token.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_access_token.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_access_token.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_access_token.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_access_token.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_access_token.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_access_token.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.tenant_id IS '租户编号';


--
-- Name: system_oauth2_access_token_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_access_token_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_approve; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_approve (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    client_id character varying(255) NOT NULL,
    scope character varying(255) DEFAULT ''::character varying NOT NULL,
    approved boolean DEFAULT false NOT NULL,
    expires_time timestamp without time zone NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_approve; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_approve IS 'OAuth2 批准表';


--
-- Name: COLUMN system_oauth2_approve.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.id IS '编号';


--
-- Name: COLUMN system_oauth2_approve.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_approve.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_approve.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_approve.scope; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.scope IS '授权范围';


--
-- Name: COLUMN system_oauth2_approve.approved; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.approved IS '是否接受';


--
-- Name: COLUMN system_oauth2_approve.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_approve.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_approve.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_approve.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_approve.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_approve.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_approve.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.tenant_id IS '租户编号';


--
-- Name: system_oauth2_approve_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_approve_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_client (
    id bigint NOT NULL,
    client_id character varying(255) NOT NULL,
    secret character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    logo character varying(255) NOT NULL,
    description character varying(255) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    access_token_validity_seconds integer NOT NULL,
    refresh_token_validity_seconds integer NOT NULL,
    redirect_uris character varying(255) NOT NULL,
    authorized_grant_types character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT NULL::character varying,
    auto_approve_scopes character varying(255) DEFAULT NULL::character varying,
    authorities character varying(255) DEFAULT NULL::character varying,
    resource_ids character varying(255) DEFAULT NULL::character varying,
    additional_information character varying(4096) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_client; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_client IS 'OAuth2 客户端表';


--
-- Name: COLUMN system_oauth2_client.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.id IS '编号';


--
-- Name: COLUMN system_oauth2_client.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_client.secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.secret IS '客户端密钥';


--
-- Name: COLUMN system_oauth2_client.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.name IS '应用名';


--
-- Name: COLUMN system_oauth2_client.logo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.logo IS '应用图标';


--
-- Name: COLUMN system_oauth2_client.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.description IS '应用描述';


--
-- Name: COLUMN system_oauth2_client.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.status IS '状态';


--
-- Name: COLUMN system_oauth2_client.access_token_validity_seconds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.access_token_validity_seconds IS '访问令牌的有效期';


--
-- Name: COLUMN system_oauth2_client.refresh_token_validity_seconds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.refresh_token_validity_seconds IS '刷新令牌的有效期';


--
-- Name: COLUMN system_oauth2_client.redirect_uris; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.redirect_uris IS '可重定向的 URI 地址';


--
-- Name: COLUMN system_oauth2_client.authorized_grant_types; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.authorized_grant_types IS '授权类型';


--
-- Name: COLUMN system_oauth2_client.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_client.auto_approve_scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.auto_approve_scopes IS '自动通过的授权范围';


--
-- Name: COLUMN system_oauth2_client.authorities; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.authorities IS '权限';


--
-- Name: COLUMN system_oauth2_client.resource_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.resource_ids IS '资源';


--
-- Name: COLUMN system_oauth2_client.additional_information; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.additional_information IS '附加信息';


--
-- Name: COLUMN system_oauth2_client.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_client.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_client.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_client.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_client.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.deleted IS '是否删除';


--
-- Name: system_oauth2_client_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_client_seq
    START WITH 43
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_code (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    code character varying(32) NOT NULL,
    client_id character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT ''::character varying,
    expires_time timestamp without time zone NOT NULL,
    redirect_uri character varying(255) DEFAULT NULL::character varying,
    state character varying(255) DEFAULT ''::character varying NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_code IS 'OAuth2 授权码表';


--
-- Name: COLUMN system_oauth2_code.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.id IS '编号';


--
-- Name: COLUMN system_oauth2_code.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_code.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_code.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.code IS '授权码';


--
-- Name: COLUMN system_oauth2_code.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_code.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_code.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_code.redirect_uri; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.redirect_uri IS '可重定向的 URI 地址';


--
-- Name: COLUMN system_oauth2_code.state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.state IS '状态';


--
-- Name: COLUMN system_oauth2_code.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_code.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_code.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_code.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_code.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_code.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.tenant_id IS '租户编号';


--
-- Name: system_oauth2_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_refresh_token (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    refresh_token character varying(32) NOT NULL,
    user_type smallint NOT NULL,
    client_id character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT NULL::character varying,
    expires_time timestamp without time zone NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_refresh_token IS 'OAuth2 刷新令牌';


--
-- Name: COLUMN system_oauth2_refresh_token.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.id IS '编号';


--
-- Name: COLUMN system_oauth2_refresh_token.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_refresh_token.refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.refresh_token IS '刷新令牌';


--
-- Name: COLUMN system_oauth2_refresh_token.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_refresh_token.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_refresh_token.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_refresh_token.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_refresh_token.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_refresh_token.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_refresh_token.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_refresh_token.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_refresh_token.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_refresh_token.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.tenant_id IS '租户编号';


--
-- Name: system_oauth2_refresh_token_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_refresh_token_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_operate_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_operate_log (
    id bigint NOT NULL,
    trace_id character varying(64) DEFAULT ''::character varying NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    type character varying(50) NOT NULL,
    sub_type character varying(50) NOT NULL,
    biz_id bigint NOT NULL,
    action character varying(2000) DEFAULT ''::character varying NOT NULL,
    success boolean DEFAULT true NOT NULL,
    extra character varying(2000) DEFAULT ''::character varying NOT NULL,
    request_method character varying(16) DEFAULT ''::character varying,
    request_url character varying(255) DEFAULT ''::character varying,
    user_ip character varying(50) DEFAULT NULL::character varying,
    user_agent character varying(512) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_operate_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_operate_log IS '操作日志记录 V2 版本';


--
-- Name: COLUMN system_operate_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.id IS '日志主键';


--
-- Name: COLUMN system_operate_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN system_operate_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_id IS '用户编号';


--
-- Name: COLUMN system_operate_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_type IS '用户类型';


--
-- Name: COLUMN system_operate_log.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.type IS '操作模块类型';


--
-- Name: COLUMN system_operate_log.sub_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.sub_type IS '操作名';


--
-- Name: COLUMN system_operate_log.biz_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.biz_id IS '操作数据模块编号';


--
-- Name: COLUMN system_operate_log.action; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.action IS '操作内容';


--
-- Name: COLUMN system_operate_log.success; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.success IS '操作结果';


--
-- Name: COLUMN system_operate_log.extra; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.extra IS '拓展字段';


--
-- Name: COLUMN system_operate_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.request_method IS '请求方法名';


--
-- Name: COLUMN system_operate_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.request_url IS '请求地址';


--
-- Name: COLUMN system_operate_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_ip IS '用户 IP';


--
-- Name: COLUMN system_operate_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN system_operate_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.creator IS '创建者';


--
-- Name: COLUMN system_operate_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.create_time IS '创建时间';


--
-- Name: COLUMN system_operate_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.updater IS '更新者';


--
-- Name: COLUMN system_operate_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.update_time IS '更新时间';


--
-- Name: COLUMN system_operate_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.deleted IS '是否删除';


--
-- Name: COLUMN system_operate_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.tenant_id IS '租户编号';


--
-- Name: system_operate_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_operate_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_post (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(50) NOT NULL,
    sort integer NOT NULL,
    status smallint NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_post; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_post IS '岗位信息表';


--
-- Name: COLUMN system_post.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.id IS '岗位ID';


--
-- Name: COLUMN system_post.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.code IS '岗位编码';


--
-- Name: COLUMN system_post.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.name IS '岗位名称';


--
-- Name: COLUMN system_post.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.sort IS '显示顺序';


--
-- Name: COLUMN system_post.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.status IS '状态（0正常 1停用）';


--
-- Name: COLUMN system_post.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.remark IS '备注';


--
-- Name: COLUMN system_post.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.creator IS '创建者';


--
-- Name: COLUMN system_post.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.create_time IS '创建时间';


--
-- Name: COLUMN system_post.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.updater IS '更新者';


--
-- Name: COLUMN system_post.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.update_time IS '更新时间';


--
-- Name: COLUMN system_post.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.deleted IS '是否删除';


--
-- Name: COLUMN system_post.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.tenant_id IS '租户编号';


--
-- Name: system_post_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_post_seq
    START WITH 8
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_role (
    id bigint NOT NULL,
    name character varying(30) NOT NULL,
    code character varying(100) NOT NULL,
    sort integer NOT NULL,
    data_scope smallint DEFAULT 1 NOT NULL,
    data_scope_dept_ids character varying(500) DEFAULT ''::character varying NOT NULL,
    status smallint NOT NULL,
    type smallint NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_role IS '角色信息表';


--
-- Name: COLUMN system_role.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.id IS '角色ID';


--
-- Name: COLUMN system_role.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.name IS '角色名称';


--
-- Name: COLUMN system_role.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.code IS '角色权限字符串';


--
-- Name: COLUMN system_role.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.sort IS '显示顺序';


--
-- Name: COLUMN system_role.data_scope; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.data_scope IS '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）';


--
-- Name: COLUMN system_role.data_scope_dept_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.data_scope_dept_ids IS '数据范围(指定部门数组)';


--
-- Name: COLUMN system_role.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.status IS '角色状态（0正常 1停用）';


--
-- Name: COLUMN system_role.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.type IS '角色类型';


--
-- Name: COLUMN system_role.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.remark IS '备注';


--
-- Name: COLUMN system_role.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.creator IS '创建者';


--
-- Name: COLUMN system_role.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.create_time IS '创建时间';


--
-- Name: COLUMN system_role.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.updater IS '更新者';


--
-- Name: COLUMN system_role.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.update_time IS '更新时间';


--
-- Name: COLUMN system_role.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.deleted IS '是否删除';


--
-- Name: COLUMN system_role.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.tenant_id IS '租户编号';


--
-- Name: system_role_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_role_menu (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_role_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_role_menu IS '角色和菜单关联表';


--
-- Name: COLUMN system_role_menu.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.id IS '自增编号';


--
-- Name: COLUMN system_role_menu.role_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.role_id IS '角色ID';


--
-- Name: COLUMN system_role_menu.menu_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.menu_id IS '菜单ID';


--
-- Name: COLUMN system_role_menu.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.creator IS '创建者';


--
-- Name: COLUMN system_role_menu.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.create_time IS '创建时间';


--
-- Name: COLUMN system_role_menu.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.updater IS '更新者';


--
-- Name: COLUMN system_role_menu.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.update_time IS '更新时间';


--
-- Name: COLUMN system_role_menu.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.deleted IS '是否删除';


--
-- Name: COLUMN system_role_menu.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.tenant_id IS '租户编号';


--
-- Name: system_role_menu_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_role_menu_seq
    START WITH 9100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_role_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_role_seq
    START WITH 163
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_channel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_channel (
    id bigint NOT NULL,
    signature character varying(12) NOT NULL,
    code character varying(63) NOT NULL,
    status smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    api_key character varying(128) NOT NULL,
    api_secret character varying(128) DEFAULT NULL::character varying,
    callback_url character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_channel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_channel IS '短信渠道';


--
-- Name: COLUMN system_sms_channel.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.id IS '编号';


--
-- Name: COLUMN system_sms_channel.signature; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.signature IS '短信签名';


--
-- Name: COLUMN system_sms_channel.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.code IS '渠道编码';


--
-- Name: COLUMN system_sms_channel.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.status IS '开启状态';


--
-- Name: COLUMN system_sms_channel.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.remark IS '备注';


--
-- Name: COLUMN system_sms_channel.api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.api_key IS '短信 API 的账号';


--
-- Name: COLUMN system_sms_channel.api_secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.api_secret IS '短信 API 的秘钥';


--
-- Name: COLUMN system_sms_channel.callback_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.callback_url IS '短信发送回调 URL';


--
-- Name: COLUMN system_sms_channel.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.creator IS '创建者';


--
-- Name: COLUMN system_sms_channel.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_channel.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.updater IS '更新者';


--
-- Name: COLUMN system_sms_channel.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_channel.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.deleted IS '是否删除';


--
-- Name: system_sms_channel_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_channel_seq
    START WITH 8
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_code (
    id bigint NOT NULL,
    mobile character varying(11) NOT NULL,
    code character varying(6) NOT NULL,
    create_ip character varying(15) NOT NULL,
    scene smallint NOT NULL,
    today_index smallint NOT NULL,
    used smallint NOT NULL,
    used_time timestamp without time zone,
    used_ip character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_code IS '手机验证码';


--
-- Name: COLUMN system_sms_code.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.id IS '编号';


--
-- Name: COLUMN system_sms_code.mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.mobile IS '手机号';


--
-- Name: COLUMN system_sms_code.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.code IS '验证码';


--
-- Name: COLUMN system_sms_code.create_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.create_ip IS '创建 IP';


--
-- Name: COLUMN system_sms_code.scene; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.scene IS '发送场景';


--
-- Name: COLUMN system_sms_code.today_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.today_index IS '今日发送的第几条';


--
-- Name: COLUMN system_sms_code.used; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.used IS '是否使用';


--
-- Name: COLUMN system_sms_code.used_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.used_time IS '使用时间';


--
-- Name: COLUMN system_sms_code.used_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.used_ip IS '使用 IP';


--
-- Name: COLUMN system_sms_code.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.creator IS '创建者';


--
-- Name: COLUMN system_sms_code.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_code.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.updater IS '更新者';


--
-- Name: COLUMN system_sms_code.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_code.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.deleted IS '是否删除';


--
-- Name: COLUMN system_sms_code.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.tenant_id IS '租户编号';


--
-- Name: system_sms_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_log (
    id bigint NOT NULL,
    channel_id bigint NOT NULL,
    channel_code character varying(63) NOT NULL,
    template_id bigint NOT NULL,
    template_code character varying(63) NOT NULL,
    template_type smallint NOT NULL,
    template_content character varying(255) NOT NULL,
    template_params character varying(255) NOT NULL,
    api_template_id character varying(63) NOT NULL,
    mobile character varying(11) NOT NULL,
    user_id bigint,
    user_type smallint,
    send_status smallint DEFAULT 0 NOT NULL,
    send_time timestamp without time zone,
    api_send_code character varying(63) DEFAULT NULL::character varying,
    api_send_msg character varying(255) DEFAULT NULL::character varying,
    api_request_id character varying(255) DEFAULT NULL::character varying,
    api_serial_no character varying(255) DEFAULT NULL::character varying,
    receive_status smallint DEFAULT 0 NOT NULL,
    receive_time timestamp without time zone,
    api_receive_code character varying(63) DEFAULT NULL::character varying,
    api_receive_msg character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_log IS '短信日志';


--
-- Name: COLUMN system_sms_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.id IS '编号';


--
-- Name: COLUMN system_sms_log.channel_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.channel_id IS '短信渠道编号';


--
-- Name: COLUMN system_sms_log.channel_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.channel_code IS '短信渠道编码';


--
-- Name: COLUMN system_sms_log.template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_id IS '模板编号';


--
-- Name: COLUMN system_sms_log.template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_code IS '模板编码';


--
-- Name: COLUMN system_sms_log.template_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_type IS '短信类型';


--
-- Name: COLUMN system_sms_log.template_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_content IS '短信内容';


--
-- Name: COLUMN system_sms_log.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_params IS '短信参数';


--
-- Name: COLUMN system_sms_log.api_template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_template_id IS '短信 API 的模板编号';


--
-- Name: COLUMN system_sms_log.mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.mobile IS '手机号';


--
-- Name: COLUMN system_sms_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.user_id IS '用户编号';


--
-- Name: COLUMN system_sms_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.user_type IS '用户类型';


--
-- Name: COLUMN system_sms_log.send_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.send_status IS '发送状态';


--
-- Name: COLUMN system_sms_log.send_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.send_time IS '发送时间';


--
-- Name: COLUMN system_sms_log.api_send_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_send_code IS '短信 API 发送结果的编码';


--
-- Name: COLUMN system_sms_log.api_send_msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_send_msg IS '短信 API 发送失败的提示';


--
-- Name: COLUMN system_sms_log.api_request_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_request_id IS '短信 API 发送返回的唯一请求 ID';


--
-- Name: COLUMN system_sms_log.api_serial_no; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_serial_no IS '短信 API 发送返回的序号';


--
-- Name: COLUMN system_sms_log.receive_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.receive_status IS '接收状态';


--
-- Name: COLUMN system_sms_log.receive_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.receive_time IS '接收时间';


--
-- Name: COLUMN system_sms_log.api_receive_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_receive_code IS 'API 接收结果的编码';


--
-- Name: COLUMN system_sms_log.api_receive_msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_receive_msg IS 'API 接收结果的说明';


--
-- Name: COLUMN system_sms_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.creator IS '创建者';


--
-- Name: COLUMN system_sms_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.updater IS '更新者';


--
-- Name: COLUMN system_sms_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.deleted IS '是否删除';


--
-- Name: system_sms_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_template (
    id bigint NOT NULL,
    type smallint NOT NULL,
    status smallint NOT NULL,
    code character varying(63) NOT NULL,
    name character varying(63) NOT NULL,
    content character varying(255) NOT NULL,
    params character varying(255) NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    api_template_id character varying(63) NOT NULL,
    channel_id bigint NOT NULL,
    channel_code character varying(63) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_template IS '短信模板';


--
-- Name: COLUMN system_sms_template.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.id IS '编号';


--
-- Name: COLUMN system_sms_template.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.type IS '模板类型';


--
-- Name: COLUMN system_sms_template.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.status IS '开启状态';


--
-- Name: COLUMN system_sms_template.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.code IS '模板编码';


--
-- Name: COLUMN system_sms_template.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.name IS '模板名称';


--
-- Name: COLUMN system_sms_template.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.content IS '模板内容';


--
-- Name: COLUMN system_sms_template.params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.params IS '参数数组';


--
-- Name: COLUMN system_sms_template.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.remark IS '备注';


--
-- Name: COLUMN system_sms_template.api_template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.api_template_id IS '短信 API 的模板编号';


--
-- Name: COLUMN system_sms_template.channel_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.channel_id IS '短信渠道编号';


--
-- Name: COLUMN system_sms_template.channel_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.channel_code IS '短信渠道编码';


--
-- Name: COLUMN system_sms_template.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.creator IS '创建者';


--
-- Name: COLUMN system_sms_template.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_template.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.updater IS '更新者';


--
-- Name: COLUMN system_sms_template.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_template.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.deleted IS '是否删除';


--
-- Name: system_sms_template_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_template_seq
    START WITH 20
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_client (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    social_type smallint NOT NULL,
    user_type smallint NOT NULL,
    client_id character varying(255) NOT NULL,
    client_secret character varying(255) NOT NULL,
    agent_id character varying(255) DEFAULT NULL::character varying,
    public_key character varying(2048) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_social_client; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_social_client IS '社交客户端表';


--
-- Name: COLUMN system_social_client.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.id IS '编号';


--
-- Name: COLUMN system_social_client.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.name IS '应用名';


--
-- Name: COLUMN system_social_client.social_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.social_type IS '社交平台的类型';


--
-- Name: COLUMN system_social_client.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.user_type IS '用户类型';


--
-- Name: COLUMN system_social_client.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.client_id IS '客户端编号';


--
-- Name: COLUMN system_social_client.client_secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.client_secret IS '客户端密钥';


--
-- Name: COLUMN system_social_client.agent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.agent_id IS '代理编号';


--
-- Name: COLUMN system_social_client.public_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.public_key IS 'publicKey 公钥';


--
-- Name: COLUMN system_social_client.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.status IS '状态';


--
-- Name: COLUMN system_social_client.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.creator IS '创建者';


--
-- Name: COLUMN system_social_client.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.create_time IS '创建时间';


--
-- Name: COLUMN system_social_client.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.updater IS '更新者';


--
-- Name: COLUMN system_social_client.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.update_time IS '更新时间';


--
-- Name: COLUMN system_social_client.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.deleted IS '是否删除';


--
-- Name: COLUMN system_social_client.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.tenant_id IS '租户编号';


--
-- Name: system_social_client_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_client_seq
    START WITH 48
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_user (
    id bigint NOT NULL,
    type smallint NOT NULL,
    openid character varying(32) NOT NULL,
    token character varying(256) DEFAULT NULL::character varying,
    raw_token_info character varying(1024) NOT NULL,
    nickname character varying(32) NOT NULL,
    avatar character varying(255) DEFAULT NULL::character varying,
    raw_user_info character varying(1024) NOT NULL,
    code character varying(256) NOT NULL,
    state character varying(256) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_social_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_social_user IS '社交用户表';


--
-- Name: COLUMN system_social_user.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.id IS '主键(自增策略)';


--
-- Name: COLUMN system_social_user.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.type IS '社交平台的类型';


--
-- Name: COLUMN system_social_user.openid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.openid IS '社交 openid';


--
-- Name: COLUMN system_social_user.token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.token IS '社交 token';


--
-- Name: COLUMN system_social_user.raw_token_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.raw_token_info IS '原始 Token 数据，一般是 JSON 格式';


--
-- Name: COLUMN system_social_user.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.nickname IS '用户昵称';


--
-- Name: COLUMN system_social_user.avatar; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.avatar IS '用户头像';


--
-- Name: COLUMN system_social_user.raw_user_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.raw_user_info IS '原始用户数据，一般是 JSON 格式';


--
-- Name: COLUMN system_social_user.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.code IS '最后一次的认证 code';


--
-- Name: COLUMN system_social_user.state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.state IS '最后一次的认证 state';


--
-- Name: COLUMN system_social_user.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.creator IS '创建者';


--
-- Name: COLUMN system_social_user.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.create_time IS '创建时间';


--
-- Name: COLUMN system_social_user.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.updater IS '更新者';


--
-- Name: COLUMN system_social_user.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.update_time IS '更新时间';


--
-- Name: COLUMN system_social_user.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.deleted IS '是否删除';


--
-- Name: COLUMN system_social_user.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.tenant_id IS '租户编号';


--
-- Name: system_social_user_bind; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_user_bind (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    social_type smallint NOT NULL,
    social_user_id bigint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_social_user_bind; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_social_user_bind IS '社交绑定表';


--
-- Name: COLUMN system_social_user_bind.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.id IS '主键(自增策略)';


--
-- Name: COLUMN system_social_user_bind.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.user_id IS '用户编号';


--
-- Name: COLUMN system_social_user_bind.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.user_type IS '用户类型';


--
-- Name: COLUMN system_social_user_bind.social_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.social_type IS '社交平台的类型';


--
-- Name: COLUMN system_social_user_bind.social_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.social_user_id IS '社交用户的编号';


--
-- Name: COLUMN system_social_user_bind.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.creator IS '创建者';


--
-- Name: COLUMN system_social_user_bind.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.create_time IS '创建时间';


--
-- Name: COLUMN system_social_user_bind.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.updater IS '更新者';


--
-- Name: COLUMN system_social_user_bind.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.update_time IS '更新时间';


--
-- Name: COLUMN system_social_user_bind.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.deleted IS '是否删除';


--
-- Name: COLUMN system_social_user_bind.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.tenant_id IS '租户编号';


--
-- Name: system_social_user_bind_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_user_bind_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_user_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_user_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_tenant (
    id bigint NOT NULL,
    name character varying(30) NOT NULL,
    contact_user_id bigint,
    contact_name character varying(30) NOT NULL,
    contact_mobile character varying(500) DEFAULT NULL::character varying,
    status smallint DEFAULT 0 NOT NULL,
    websites character varying(1024) DEFAULT ''::character varying,
    package_id bigint NOT NULL,
    expire_time timestamp without time zone NOT NULL,
    account_count integer NOT NULL,
    creator character varying(64) DEFAULT ''::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_tenant; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_tenant IS '租户表';


--
-- Name: COLUMN system_tenant.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.id IS '租户编号';


--
-- Name: COLUMN system_tenant.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.name IS '租户名';


--
-- Name: COLUMN system_tenant.contact_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.contact_user_id IS '联系人的用户编号';


--
-- Name: COLUMN system_tenant.contact_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.contact_name IS '联系人';


--
-- Name: COLUMN system_tenant.contact_mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.contact_mobile IS '联系手机';


--
-- Name: COLUMN system_tenant.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.status IS '租户状态';


--
-- Name: COLUMN system_tenant.websites; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.websites IS '绑定域名数组';


--
-- Name: COLUMN system_tenant.package_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.package_id IS '租户套餐编号';


--
-- Name: COLUMN system_tenant.expire_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.expire_time IS '过期时间';


--
-- Name: COLUMN system_tenant.account_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.account_count IS '账号数量';


--
-- Name: COLUMN system_tenant.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.creator IS '创建者';


--
-- Name: COLUMN system_tenant.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.create_time IS '创建时间';


--
-- Name: COLUMN system_tenant.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.updater IS '更新者';


--
-- Name: COLUMN system_tenant.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.update_time IS '更新时间';


--
-- Name: COLUMN system_tenant.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.deleted IS '是否删除';


--
-- Name: system_tenant_package; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_tenant_package (
    id bigint NOT NULL,
    name character varying(30) NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(256) DEFAULT ''::character varying,
    menu_ids character varying(4096) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_tenant_package; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_tenant_package IS '租户套餐表';


--
-- Name: COLUMN system_tenant_package.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.id IS '套餐编号';


--
-- Name: COLUMN system_tenant_package.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.name IS '套餐名';


--
-- Name: COLUMN system_tenant_package.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.status IS '租户状态（0正常 1停用）';


--
-- Name: COLUMN system_tenant_package.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.remark IS '备注';


--
-- Name: COLUMN system_tenant_package.menu_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.menu_ids IS '关联的菜单编号';


--
-- Name: COLUMN system_tenant_package.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.creator IS '创建者';


--
-- Name: COLUMN system_tenant_package.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.create_time IS '创建时间';


--
-- Name: COLUMN system_tenant_package.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.updater IS '更新者';


--
-- Name: COLUMN system_tenant_package.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.update_time IS '更新时间';


--
-- Name: COLUMN system_tenant_package.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.deleted IS '是否删除';


--
-- Name: system_tenant_package_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_tenant_package_seq
    START WITH 114
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_tenant_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_tenant_seq
    START WITH 123
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_user_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_user_post (
    id bigint NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    post_id bigint DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_user_post; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_user_post IS '用户岗位表';


--
-- Name: COLUMN system_user_post.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.id IS 'id';


--
-- Name: COLUMN system_user_post.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.user_id IS '用户ID';


--
-- Name: COLUMN system_user_post.post_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.post_id IS '岗位ID';


--
-- Name: COLUMN system_user_post.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.creator IS '创建者';


--
-- Name: COLUMN system_user_post.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.create_time IS '创建时间';


--
-- Name: COLUMN system_user_post.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.updater IS '更新者';


--
-- Name: COLUMN system_user_post.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.update_time IS '更新时间';


--
-- Name: COLUMN system_user_post.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.deleted IS '是否删除';


--
-- Name: COLUMN system_user_post.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.tenant_id IS '租户编号';


--
-- Name: system_user_post_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_user_post_seq
    START WITH 130
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_user_role (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_user_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_user_role IS '用户和角色关联表';


--
-- Name: COLUMN system_user_role.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.id IS '自增编号';


--
-- Name: COLUMN system_user_role.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.user_id IS '用户ID';


--
-- Name: COLUMN system_user_role.role_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.role_id IS '角色ID';


--
-- Name: COLUMN system_user_role.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.creator IS '创建者';


--
-- Name: COLUMN system_user_role.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.create_time IS '创建时间';


--
-- Name: COLUMN system_user_role.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.updater IS '更新者';


--
-- Name: COLUMN system_user_role.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.update_time IS '更新时间';


--
-- Name: COLUMN system_user_role.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.deleted IS '是否删除';


--
-- Name: COLUMN system_user_role.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.tenant_id IS '租户编号';


--
-- Name: system_user_role_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_user_role_seq
    START WITH 84
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_users (
    id bigint NOT NULL,
    username character varying(30) NOT NULL,
    password character varying(100) DEFAULT ''::character varying NOT NULL,
    nickname character varying(30) NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    dept_id bigint,
    post_ids character varying(255) DEFAULT NULL::character varying,
    email character varying(50) DEFAULT ''::character varying,
    mobile character varying(11) DEFAULT ''::character varying,
    sex smallint DEFAULT 0,
    avatar character varying(512) DEFAULT ''::character varying,
    status smallint DEFAULT 0 NOT NULL,
    login_ip character varying(50) DEFAULT ''::character varying,
    login_date timestamp without time zone,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_users IS '用户信息表';


--
-- Name: COLUMN system_users.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.id IS '用户ID';


--
-- Name: COLUMN system_users.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.username IS '用户账号';


--
-- Name: COLUMN system_users.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.password IS '密码';


--
-- Name: COLUMN system_users.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.nickname IS '用户昵称';


--
-- Name: COLUMN system_users.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.remark IS '备注';


--
-- Name: COLUMN system_users.dept_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.dept_id IS '部门ID';


--
-- Name: COLUMN system_users.post_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.post_ids IS '岗位编号数组';


--
-- Name: COLUMN system_users.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.email IS '用户邮箱';


--
-- Name: COLUMN system_users.mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.mobile IS '手机号码';


--
-- Name: COLUMN system_users.sex; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.sex IS '用户性别';


--
-- Name: COLUMN system_users.avatar; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.avatar IS '头像地址';


--
-- Name: COLUMN system_users.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.status IS '帐号状态（0正常 1停用）';


--
-- Name: COLUMN system_users.login_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.login_ip IS '最后登录IP';


--
-- Name: COLUMN system_users.login_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.login_date IS '最后登录时间';


--
-- Name: COLUMN system_users.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.creator IS '创建者';


--
-- Name: COLUMN system_users.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.create_time IS '创建时间';


--
-- Name: COLUMN system_users.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.updater IS '更新者';


--
-- Name: COLUMN system_users.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.update_time IS '更新时间';


--
-- Name: COLUMN system_users.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.deleted IS '是否删除';


--
-- Name: COLUMN system_users.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.tenant_id IS '租户编号';


--
-- Name: system_users_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_users_seq
    START WITH 241
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Data for Name: dual; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.dual (id) VALUES (1);


--
-- Data for Name: infra_api_access_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_api_error_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_codegen_column; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_codegen_table; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.infra_config (id, category, type, name, config_key, value, visible, remark, creator, create_time, updater, update_time, deleted) VALUES (2, 'biz', 1, '用户管理-账号初始密码', 'system.user.init-password', '123456', false, '初始化密码 123456', 'admin', '2021-01-05 17:03:48', '1', '2024-07-20 17:22:47', 0);
INSERT INTO public.infra_config (id, category, type, name, config_key, value, visible, remark, creator, create_time, updater, update_time, deleted) VALUES (13, '用户管理-账号初始密码', 2, '用户管理-注册开关', 'system.user.register-enabled', 'true', false, '', '1', '2025-04-26 17:23:41', '1', '2025-04-26 17:23:41', 0);


--
-- Data for Name: infra_data_source_config; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_file; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_file_config; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.infra_file_config (id, name, storage, remark, master, config, creator, create_time, updater, update_time, deleted) VALUES (4, '数据库（示例）', 1, '我是数据库', true, '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"http://127.0.0.1:48080"}', '1', '2022-03-15 23:56:24', '1', '2026-09-01 16:54:50', 0);


--
-- Data for Name: infra_file_content; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_job; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: infra_job_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_dept; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_dept (id, name, parent_id, sort, leader_user_id, phone, email, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (100, '芋道源码', 0, 0, 1, '15888888888', 'ry@qq.com', 0, 'admin', '2021-01-05 17:03:47', '1', '2026-01-04 18:01:12', 0, 1);
INSERT INTO public.system_dept (id, name, parent_id, sort, leader_user_id, phone, email, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (101, '深圳总公司', 100, 1, 1, '15888888888', 'ry@qq.com', 0, 'admin', '2021-01-05 17:03:47', '1', '2025-03-29 15:49:55', 0, 1);
INSERT INTO public.system_dept (id, name, parent_id, sort, leader_user_id, phone, email, status, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (103, '研发部门', 101, 1, 1, '15888888888', 'ry@qq.com', 0, 'admin', '2021-01-05 17:03:47', '1', '2026-01-04 18:01:24', 0, 1);


--
-- Data for Name: system_dict_data; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (1, 1, '男', '1', 'system_user_sex', 0, 'primary', 'A', '性别男', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (2, 2, '女', '2', 'system_user_sex', 0, 'success', '', '性别女', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (3, 1, '正常', '1', 'infra_job_status', 0, 'success', '', '正常状态', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (4, 2, '暂停', '2', 'infra_job_status', 0, 'danger', '', '停用状态', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (5, 1, '系统内置', '1', 'infra_config_type', 0, 'danger', '', '参数类型 - 系统内置', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (6, 2, '自定义', '2', 'infra_config_type', 0, 'primary', '', '参数类型 - 自定义', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (7, 1, '通知', '1', 'system_notice_type', 0, 'success', '', '通知', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (8, 2, '公告', '2', 'system_notice_type', 0, 'info', '', '公告', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (9, 0, '其它', '0', 'infra_operate_type', 0, 'default', '', '其它操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (10, 1, '查询', '1', 'infra_operate_type', 0, 'info', '', '查询操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (11, 2, '新增', '2', 'infra_operate_type', 0, 'primary', '', '新增操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (12, 3, '修改', '3', 'infra_operate_type', 0, 'warning', '', '修改操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (13, 4, '删除', '4', 'infra_operate_type', 0, 'danger', '', '删除操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (14, 5, '导出', '5', 'infra_operate_type', 0, 'default', '', '导出操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (15, 6, '导入', '6', 'infra_operate_type', 0, 'default', '', '导入操作', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (16, 1, '开启', '0', 'common_status', 0, 'primary', '', '开启状态', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (17, 2, '关闭', '1', 'common_status', 0, 'info', '', '关闭状态', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (18, 1, '目录', '1', 'system_menu_type', 0, '', '', '目录', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (19, 2, '菜单', '2', 'system_menu_type', 0, '', '', '菜单', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (20, 3, '按钮', '3', 'system_menu_type', 0, '', '', '按钮', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (21, 1, '内置', '1', 'system_role_type', 0, 'danger', '', '内置角色', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (22, 2, '自定义', '2', 'system_role_type', 0, 'primary', '', '自定义角色', 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (23, 1, '全部数据权限', '1', 'system_data_scope', 0, '', '', '全部数据权限', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (24, 2, '指定部门数据权限', '2', 'system_data_scope', 0, '', '', '指定部门数据权限', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (25, 3, '本部门数据权限', '3', 'system_data_scope', 0, '', '', '本部门数据权限', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (26, 4, '本部门及以下数据权限', '4', 'system_data_scope', 0, '', '', '本部门及以下数据权限', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (27, 5, '仅本人数据权限', '5', 'system_data_scope', 0, '', '', '仅本人数据权限', 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (28, 0, '成功', '0', 'system_login_result', 0, 'success', '', '登陆结果 - 成功', '', '2021-01-18 06:17:36', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (29, 10, '账号或密码不正确', '10', 'system_login_result', 0, 'primary', '', '登陆结果 - 账号或密码不正确', '', '2021-01-18 06:17:54', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (30, 20, '用户被禁用', '20', 'system_login_result', 0, 'warning', '', '登陆结果 - 用户被禁用', '', '2021-01-18 06:17:54', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (31, 30, '验证码不存在', '30', 'system_login_result', 0, 'info', '', '登陆结果 - 验证码不存在', '', '2021-01-18 06:17:54', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (32, 31, '验证码不正确', '31', 'system_login_result', 0, 'info', '', '登陆结果 - 验证码不正确', '', '2021-01-18 06:17:54', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (33, 100, '未知异常', '100', 'system_login_result', 0, 'danger', '', '登陆结果 - 未知异常', '', '2021-01-18 06:17:54', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (34, 1, '是', 'true', 'infra_boolean_string', 0, 'danger', '', 'Boolean 是否类型 - 是', '', '2021-01-19 03:20:55', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (35, 1, '否', 'false', 'infra_boolean_string', 0, 'info', '', 'Boolean 是否类型 - 否', '', '2021-01-19 03:20:55', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (36, 1, '单表（增删改查）', '1', 'infra_codegen_template_type', 0, '', '', NULL, '', '2021-02-05 07:09:06', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (37, 2, '树表（增删改查）', '2', 'infra_codegen_template_type', 0, '', '', NULL, '', '2021-02-05 07:14:46', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (38, 0, '初始化中', '0', 'infra_job_status', 0, 'primary', '', NULL, '', '2021-02-07 07:46:49', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (39, 0, '运行中', '0', 'infra_job_log_status', 0, 'primary', '', 'RUNNING', '', '2021-02-08 10:04:24', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (40, 1, '成功', '1', 'infra_job_log_status', 0, 'success', '', NULL, '', '2021-02-08 10:06:57', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (41, 2, '失败', '2', 'infra_job_log_status', 0, 'warning', '', '失败', '', '2021-02-08 10:07:38', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (42, 1, '会员', '1', 'user_type', 0, 'primary', '', NULL, '', '2021-02-26 00:16:27', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (43, 2, '管理员', '2', 'user_type', 0, 'success', '', NULL, '', '2021-02-26 00:16:34', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (44, 0, '未处理', '0', 'infra_api_error_log_process_status', 0, 'primary', '', NULL, '', '2021-02-26 07:07:19', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (45, 1, '已处理', '1', 'infra_api_error_log_process_status', 0, 'success', '', NULL, '', '2021-02-26 07:07:26', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (46, 2, '已忽略', '2', 'infra_api_error_log_process_status', 0, 'danger', '', NULL, '', '2021-02-26 07:07:34', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (47, 1, '阿里云', 'ALIYUN', 'system_sms_channel_code', 0, 'primary', '', NULL, '1', '2021-04-05 01:05:26', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (48, 1, '验证码', '1', 'system_sms_template_type', 0, 'warning', '', NULL, '1', '2021-04-05 21:50:57', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (49, 2, '通知', '2', 'system_sms_template_type', 0, 'primary', '', NULL, '1', '2021-04-05 21:51:08', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (50, 0, '营销', '3', 'system_sms_template_type', 0, 'danger', '', NULL, '1', '2021-04-05 21:51:15', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (51, 0, '初始化', '0', 'system_sms_send_status', 0, 'primary', '', NULL, '1', '2021-04-11 20:18:33', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (52, 1, '发送成功', '10', 'system_sms_send_status', 0, 'success', '', NULL, '1', '2021-04-11 20:18:43', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (53, 2, '发送失败', '20', 'system_sms_send_status', 0, 'danger', '', NULL, '1', '2021-04-11 20:18:49', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (54, 3, '不发送', '30', 'system_sms_send_status', 0, 'info', '', NULL, '1', '2021-04-11 20:19:44', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (55, 0, '等待结果', '0', 'system_sms_receive_status', 0, 'primary', '', NULL, '1', '2021-04-11 20:27:43', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (56, 1, '接收成功', '10', 'system_sms_receive_status', 0, 'success', '', NULL, '1', '2021-04-11 20:29:25', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (57, 2, '接收失败', '20', 'system_sms_receive_status', 0, 'danger', '', NULL, '1', '2021-04-11 20:29:31', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (58, 0, '调试(钉钉)', 'DEBUG_DING_TALK', 'system_sms_channel_code', 0, 'info', '', NULL, '1', '2021-04-13 00:20:37', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (59, 100, '账号登录', '100', 'system_login_type', 0, 'primary', '', '账号登录', '1', '2021-10-06 00:52:02', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (60, 101, '社交登录', '101', 'system_login_type', 0, 'info', '', '社交登录', '1', '2021-10-06 00:52:17', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (61, 200, '主动登出', '200', 'system_login_type', 0, 'primary', '', '主动登出', '1', '2021-10-06 00:52:58', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (62, 202, '强制登出', '202', 'system_login_type', 0, 'danger', '', '强制退出', '1', '2021-10-06 00:53:41', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (103, 1, '管理后台', '1', 'infra_codegen_scene', 0, '', '', '代码生成的场景枚举 - 管理后台', '1', '2022-02-02 13:15:06', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (104, 2, '用户 APP', '2', 'infra_codegen_scene', 0, '', '', '代码生成的场景枚举 - 用户 APP', '1', '2022-02-02 13:15:19', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (105, 1, '数据库', '1', 'infra_file_storage', 0, 'default', '', NULL, '1', '2022-03-15 00:25:28', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (106, 10, '本地磁盘', '10', 'infra_file_storage', 0, 'default', '', NULL, '1', '2022-03-15 00:25:41', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (107, 11, 'FTP 服务器', '11', 'infra_file_storage', 0, 'default', '', NULL, '1', '2022-03-15 00:26:06', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (108, 12, 'SFTP 服务器', '12', 'infra_file_storage', 0, 'default', '', NULL, '1', '2022-03-15 00:26:22', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (109, 20, 'S3 对象存储', '20', 'infra_file_storage', 0, 'default', '', NULL, '1', '2022-03-15 00:26:31', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (110, 103, '短信登录', '103', 'system_login_type', 0, 'default', '', NULL, '1', '2022-05-09 23:57:58', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (111, 1, 'password', 'password', 'system_oauth2_grant_type', 0, 'default', '', '密码模式', '1', '2022-05-12 00:22:05', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (112, 2, 'authorization_code', 'authorization_code', 'system_oauth2_grant_type', 0, 'primary', '', '授权码模式', '1', '2022-05-12 00:22:59', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (113, 3, 'implicit', 'implicit', 'system_oauth2_grant_type', 0, 'success', '', '简化模式', '1', '2022-05-12 00:23:40', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (114, 4, 'client_credentials', 'client_credentials', 'system_oauth2_grant_type', 0, 'default', '', '客户端模式', '1', '2022-05-12 00:23:51', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (115, 5, 'refresh_token', 'refresh_token', 'system_oauth2_grant_type', 0, 'info', '', '刷新模式', '1', '2022-05-12 00:24:02', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (148, 10, '微信小程序', '10', 'terminal', 0, 'default', '', '终端 - 微信小程序', '1', '2022-12-10 10:51:11', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (149, 20, 'H5 网页', '20', 'terminal', 0, 'default', '', '终端 - H5 网页', '1', '2022-12-10 10:51:30', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (150, 11, '微信公众号', '11', 'terminal', 0, 'default', '', '终端 - 微信公众号', '1', '2022-12-10 10:54:16', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (151, 31, '苹果 App', '31', 'terminal', 0, 'default', '', '终端 - 苹果 App', '1', '2022-12-10 10:54:42', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (152, 32, '安卓 App', '32', 'terminal', 0, 'default', '', '终端 - 安卓 App', '1', '2022-12-10 10:55:02', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (177, 0, '初始化', '0', 'system_mail_send_status', 0, 'primary', '', '邮件发送状态 - 初始化\n', '1', '2023-01-26 09:53:49', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (178, 10, '发送成功', '10', 'system_mail_send_status', 0, 'success', '', '邮件发送状态 - 发送成功', '1', '2023-01-26 09:54:28', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (179, 20, '发送失败', '20', 'system_mail_send_status', 0, 'danger', '', '邮件发送状态 - 发送失败', '1', '2023-01-26 09:54:50', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (180, 30, '不发送', '30', 'system_mail_send_status', 0, 'info', '', '邮件发送状态 -  不发送', '1', '2023-01-26 09:55:06', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (181, 1, '通知公告', '1', 'system_notify_template_type', 0, 'primary', '', '站内信模版的类型 - 通知公告', '1', '2023-01-28 10:35:59', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (182, 2, '系统消息', '2', 'system_notify_template_type', 0, 'success', '', '站内信模版的类型 - 系统消息', '1', '2023-01-28 10:36:20', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (184, 10, 'Vue2 Element UI 标准模版', '10', 'infra_codegen_front_type', 0, '', '', '', '1', '2023-04-13 00:03:55', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (185, 20, 'Vue3 Element Plus 标准模版', '20', 'infra_codegen_front_type', 0, '', '', '', '1', '2023-04-13 00:04:08', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (186, 30, 'Vben2.0 Ant Design Schema 模版', '30', 'infra_codegen_front_type', 1, '', '', '', '1', '2023-04-13 00:04:26', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (274, 10, 'Gitee', '10', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:04:42', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (275, 20, '钉钉', '20', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:04:54', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (276, 30, '企业微信', '30', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:05:09', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (277, 31, '微信公众平台', '31', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:05:18', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (278, 32, '微信开放平台', '32', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:05:30', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (279, 34, '微信小程序', '34', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:05:38', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (282, 15, '子表', '15', 'infra_codegen_template_type', 0, 'default', '', '', '1', '2023-11-13 23:06:16', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (283, 10, '主表（标准模式）', '10', 'infra_codegen_template_type', 0, 'default', '', '', '1', '2023-11-14 12:32:49', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (284, 11, '主表（ERP 模式）', '11', 'infra_codegen_template_type', 0, 'default', '', '', '1', '2023-11-14 12:33:05', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (285, 12, '主表（内嵌模式）', '12', 'infra_codegen_template_type', 0, '', '', '', '1', '2023-11-14 12:33:31', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (353, 1, '天', '1', 'date_interval', 0, '', '', '', '1', '2024-03-29 22:50:26', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (354, 2, '周', '2', 'date_interval', 0, '', '', '', '1', '2024-03-29 22:50:36', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (355, 3, '月', '3', 'date_interval', 0, '', '', '', '1', '2024-03-29 22:50:46', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (356, 4, '季度', '4', 'date_interval', 0, '', '', '', '1', '2024-03-29 22:51:01', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (357, 5, '年', '5', 'date_interval', 0, '', '', '', '1', '2024-03-29 22:51:07', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (407, 2, '腾讯云', 'TENCENT', 'system_sms_channel_code', 0, '', '', '', '1', '2024-07-22 22:23:16', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (408, 3, '华为云', 'HUAWEI', 'system_sms_channel_code', 0, '', '', '', '1', '2024-07-22 22:23:46', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (412, 4, '七牛云', 'QINIU', 'system_sms_channel_code', 0, '', '', '', '1', '2024-08-31 08:45:03', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (596, 40, 'Vben5.0 Ant Design Schema 模版', '40', 'infra_codegen_front_type', 0, '', '', NULL, '1', '2025-04-23 21:47:47', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (622, 41, 'Vben5.0 Ant Design 标准模版', '41', 'infra_codegen_front_type', 0, '', '', '', '1', '2025-09-04 23:26:07', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (623, 50, 'Vben5.0 Element Plus Schema 模版', '50', 'infra_codegen_front_type', 0, '', '', '', '1', '2025-09-04 23:26:38', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (624, 51, 'Vben5.0 Element Plus 标准模版', '51', 'infra_codegen_front_type', 0, '', '', '', '1', '2025-09-04 23:26:49', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (626, 40, '支付宝小程序', '40', 'system_social_type', 0, '', '', '', '1', '2023-11-04 13:05:38', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (627, 60, 'Admin Uniapp 移动端', '60', 'infra_codegen_front_type', 0, '', '', NULL, '1', '2025-12-16 19:25:51', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (628, 42, 'Vben5.0 Antdv Next Schema 模版', '42', 'infra_codegen_front_type', 0, '', '', '', '1', '2026-05-23 13:52:25', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) VALUES (629, 43, 'Vben5.0 Antdv Next 标准模版', '43', 'infra_codegen_front_type', 0, '', '', '', '1', '2026-05-23 13:52:25', '1', '2026-08-14 01:49:42', 0);


--
-- Data for Name: system_dict_type; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (1, '用户性别', 'system_user_sex', 0, NULL, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (2, '参数类型', 'infra_config_type', 0, NULL, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (3, '通知类型', 'system_notice_type', 0, NULL, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (4, '操作类型', 'infra_operate_type', 0, NULL, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (5, '系统状态', 'common_status', 0, NULL, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (6, 'Boolean 是否类型', 'infra_boolean_string', 0, 'boolean 转是否', '', '2021-01-19 03:20:08', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (7, '登陆结果', 'system_login_result', 0, '登陆结果', '', '2021-01-18 06:17:11', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (8, '代码生成模板类型', 'infra_codegen_template_type', 0, NULL, '', '2021-02-05 07:08:06', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (9, '定时任务状态', 'infra_job_status', 0, NULL, '', '2021-02-07 07:44:16', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (10, '定时任务日志状态', 'infra_job_log_status', 0, NULL, '', '2021-02-08 10:03:51', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (11, '用户类型', 'user_type', 0, NULL, '', '2021-02-26 00:15:51', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (12, 'API 异常数据的处理状态', 'infra_api_error_log_process_status', 0, NULL, '', '2021-02-26 07:07:01', '', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (13, '短信渠道编码', 'system_sms_channel_code', 0, NULL, '1', '2021-04-05 01:04:50', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (14, '短信模板的类型', 'system_sms_template_type', 0, NULL, '1', '2021-04-05 21:50:43', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (15, '短信发送状态', 'system_sms_send_status', 0, NULL, '1', '2021-04-11 20:18:03', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (16, '短信接收状态', 'system_sms_receive_status', 0, NULL, '1', '2021-04-11 20:27:14', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (17, '登陆日志的类型', 'system_login_type', 0, '登陆日志的类型', '1', '2021-10-06 00:50:46', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (27, '代码生成的场景枚举', 'infra_codegen_scene', 0, '代码生成的场景枚举', '1', '2022-02-02 13:14:45', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (28, '角色类型', 'system_role_type', 0, '角色类型', '1', '2022-02-16 13:01:46', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (29, '文件存储器', 'infra_file_storage', 0, '文件存储器', '1', '2022-03-15 00:24:38', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (30, 'OAuth 2.0 授权类型', 'system_oauth2_grant_type', 0, 'OAuth 2.0 授权类型（模式）', '1', '2022-05-12 00:20:52', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (42, '终端', 'terminal', 0, '终端', '1', '2022-12-10 10:50:50', '1', '2026-08-14 01:49:42', 0, NULL);
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (48, '邮件发送状态', 'system_mail_send_status', 0, '邮件发送状态', '1', '2023-01-26 09:53:13', '1', '2026-08-14 01:49:42', 0, '1970-01-01 00:00:00');
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (49, '站内信模版的类型', 'system_notify_template_type', 0, '站内信模版的类型', '1', '2023-01-28 10:35:10', '1', '2026-08-14 01:49:42', 0, '1970-01-01 00:00:00');
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (50, '代码生成的前端类型', 'infra_codegen_front_type', 0, '', '1', '2023-04-12 23:57:52', '1', '2026-08-14 01:49:42', 0, '1970-01-01 00:00:00');
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (70, '社交类型', 'system_social_type', 0, '', '1', '2023-11-04 13:03:54', '1', '2026-08-14 01:49:42', 0, '1970-01-01 00:00:00');
INSERT INTO public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) VALUES (81, '时间间隔', 'date_interval', 0, '', '1', '2024-03-29 22:50:09', '1', '2026-08-14 01:49:42', 0, '1970-01-01 00:00:00');


--
-- Data for Name: system_login_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_mail_account; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_mail_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_mail_template; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_menu; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (1, '系统管理', '', 1, 10, 0, '/system', 'ep:tools', NULL, NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-09-02 18:42:01', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (2, '基础设施', '', 1, 20, 0, '/infra', 'ep:monitor', NULL, NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-09-02 18:42:01', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (4, '用户管理', 'system:user:list', 2, 1, 1, 'user', 'ep:avatar', 'system/user/index', 'SystemUser', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (5, '角色管理', '', 2, 2, 1, 'role', 'ep:user', 'system/role/index', 'SystemRole', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (6, '菜单管理', '', 2, 3, 1, 'menu', 'ep:menu', 'system/menu/index', 'SystemMenu', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (7, '部门管理', '', 2, 4, 1, 'dept', 'fa:address-card', 'system/dept/index', 'SystemDept', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (8, '岗位管理', '', 2, 5, 1, 'post', 'fa:address-book-o', 'system/post/index', 'SystemPost', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (9, '字典管理', '', 2, 6, 1, 'dict', 'ep:collection', 'system/dict/index', 'SystemDictType', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (10, '配置管理', '', 2, 8, 2, 'config', 'fa:connectdevelop', 'infra/config/index', 'InfraConfig', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (11, '通知公告', '', 2, 4, 772, 'notice', 'ep:takeaway-box', 'system/notice/index', 'SystemNotice', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12, '审计日志', '', 1, 9, 1, 'log', 'ep:document-copy', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13, '令牌管理', '', 2, 2, 201, 'token', 'fa:key', 'system/oauth2/token/index', 'SystemTokenClient', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (14, '定时任务', '', 2, 7, 2, 'job', 'fa-solid:tasks', 'infra/job/index', 'InfraJob', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (15, 'MySQL 监控', '', 2, 1, 773, 'druid', 'fa-solid:box', 'infra/druid/index', 'InfraDruid', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (16, 'Java 监控', '', 2, 3, 773, 'admin-server', 'ep:coffee-cup', 'infra/server/index', 'InfraAdminServer', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (17, 'Redis 监控', '', 2, 2, 773, 'redis', 'fa:reddit-square', 'infra/redis/index', 'InfraRedis', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (18, '表单构建', 'infra:build:list', 2, 2, 2, 'build', 'fa:wpforms', 'infra/build/index', 'InfraBuild', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (19, '代码生成', 'infra:codegen:query', 2, 1, 2, 'codegen', 'ep:document-copy', 'infra/codegen/index', 'InfraCodegen', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (20, 'API 接口', 'infra:swagger:list', 2, 3, 2, 'swagger', 'fa:fighter-jet', 'infra/swagger/index', 'InfraSwagger', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (21, '操作日志', '', 2, 1, 12, 'operate-log', 'ep:position', 'system/operatelog/index', 'SystemOperateLog', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (22, '登录日志', '', 2, 2, 12, 'login-log', 'ep:promotion', 'system/loginlog/index', 'SystemLoginLog', 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (23, '用户查询', 'system:user:query', 3, 1, 4, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (24, '用户新增', 'system:user:create', 3, 2, 4, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (25, '用户修改', 'system:user:update', 3, 3, 4, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (26, '用户删除', 'system:user:delete', 3, 4, 4, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (27, '用户导出', 'system:user:export', 3, 5, 4, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (28, '用户导入', 'system:user:import', 3, 6, 4, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (29, '重置密码', 'system:user:update-password', 3, 7, 4, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (30, '角色查询', 'system:role:query', 3, 1, 5, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (31, '角色新增', 'system:role:create', 3, 2, 5, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (32, '角色修改', 'system:role:update', 3, 3, 5, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (33, '角色删除', 'system:role:delete', 3, 4, 5, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (34, '角色导出', 'system:role:export', 3, 5, 5, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (35, '菜单查询', 'system:menu:query', 3, 1, 6, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (36, '菜单新增', 'system:menu:create', 3, 2, 6, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (37, '菜单修改', 'system:menu:update', 3, 3, 6, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (38, '菜单删除', 'system:menu:delete', 3, 4, 6, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (39, '部门查询', 'system:dept:query', 3, 1, 7, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (40, '部门新增', 'system:dept:create', 3, 2, 7, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (41, '部门修改', 'system:dept:update', 3, 3, 7, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (42, '部门删除', 'system:dept:delete', 3, 4, 7, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (43, '岗位查询', 'system:post:query', 3, 1, 8, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (44, '岗位新增', 'system:post:create', 3, 2, 8, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (45, '岗位修改', 'system:post:update', 3, 3, 8, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (46, '岗位删除', 'system:post:delete', 3, 4, 8, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (47, '岗位导出', 'system:post:export', 3, 5, 8, '', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (48, '字典查询', 'system:dict:query', 3, 1, 9, '#', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (49, '字典新增', 'system:dict:create', 3, 2, 9, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (50, '字典修改', 'system:dict:update', 3, 3, 9, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (51, '字典删除', 'system:dict:delete', 3, 4, 9, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (52, '字典导出', 'system:dict:export', 3, 5, 9, '#', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (53, '配置查询', 'infra:config:query', 3, 1, 10, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (54, '配置新增', 'infra:config:create', 3, 2, 10, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (55, '配置修改', 'infra:config:update', 3, 3, 10, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (56, '配置删除', 'infra:config:delete', 3, 4, 10, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (57, '配置导出', 'infra:config:export', 3, 5, 10, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (58, '公告查询', 'system:notice:query', 3, 1, 11, '#', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (59, '公告新增', 'system:notice:create', 3, 2, 11, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (60, '公告修改', 'system:notice:update', 3, 3, 11, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (61, '公告删除', 'system:notice:delete', 3, 4, 11, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (62, '操作查询', 'system:operate-log:query', 3, 1, 21, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (63, '日志导出', 'system:operate-log:export', 3, 2, 21, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (64, '登录查询', 'system:login-log:query', 3, 1, 22, '#', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (65, '日志导出', 'system:login-log:export', 3, 3, 22, '#', '#', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (66, '令牌列表', 'system:oauth2-token:page', 3, 1, 13, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (67, '令牌删除', 'system:oauth2-token:delete', 3, 2, 13, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (68, '任务新增', 'infra:job:create', 3, 2, 14, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (69, '任务修改', 'infra:job:update', 3, 3, 14, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (70, '任务删除', 'infra:job:delete', 3, 4, 14, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (71, '状态修改', 'infra:job:update', 3, 5, 14, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (72, '任务导出', 'infra:job:export', 3, 7, 14, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (73, '生成修改', 'infra:codegen:update', 3, 2, 19, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (74, '生成删除', 'infra:codegen:delete', 3, 3, 19, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (75, '导入代码', 'infra:codegen:create', 3, 2, 19, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (76, '预览代码', 'infra:codegen:preview', 3, 4, 19, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (77, '生成代码', 'infra:codegen:download', 3, 5, 19, '', '', '', NULL, 0, true, true, true, 'admin', '2021-01-05 17:03:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (78, '设置角色菜单权限', 'system:permission:assign-role-menu', 3, 6, 5, '', '', '', NULL, 0, true, true, true, '', '2021-01-06 17:53:44', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (79, '设置角色数据权限', 'system:permission:assign-role-data-scope', 3, 7, 5, '', '', '', NULL, 0, true, true, true, '', '2021-01-06 17:56:31', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (80, '设置用户角色', 'system:permission:assign-user-role', 3, 8, 5, '', '', '', NULL, 0, true, true, true, '', '2021-01-07 10:23:28', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (81, '获得 Redis 监控信息', 'infra:redis:get-monitor-info', 3, 1, 17, '', '', '', NULL, 0, true, true, true, '', '2021-01-26 01:02:31', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (82, '获得 Redis Key 列表', 'infra:redis:get-key-list', 3, 2, 17, '', '', '', NULL, 0, true, true, true, '', '2021-01-26 01:02:52', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (83, '代码生成案例', '', 1, 1, 2, 'demo', 'ep:aim', 'infra/testDemo/index', NULL, 0, true, true, true, '', '2021-02-06 12:42:49', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (84, '任务触发', 'infra:job:trigger', 3, 8, 14, '', '', '', NULL, 0, true, true, true, '', '2021-02-07 13:03:10', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (85, '链路追踪', '', 2, 4, 773, 'skywalking', 'fa:eye', 'infra/skywalking/index', 'InfraSkyWalking', 0, true, true, true, '', '2021-02-08 20:41:31', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (86, '访问日志', '', 2, 1, 88, 'api-access-log', 'ep:place', 'infra/apiAccessLog/index', 'InfraApiAccessLog', 0, true, true, true, '', '2021-02-26 01:32:59', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (87, '日志导出', 'infra:api-access-log:export', 3, 2, 86, '', '', '', NULL, 0, true, true, true, '', '2021-02-26 01:32:59', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (88, 'API 日志', '', 2, 4, 2, 'log', 'fa:tasks', NULL, NULL, 0, true, true, true, '', '2021-02-26 02:18:24', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (89, '错误日志', 'infra:api-error-log:query', 2, 2, 88, 'api-error-log', 'ep:warning-filled', 'infra/apiErrorLog/index', 'InfraApiErrorLog', 0, true, true, true, '', '2021-02-26 07:53:20', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (90, '日志处理', 'infra:api-error-log:update-status', 3, 2, 89, '', '', '', NULL, 0, true, true, true, '', '2021-02-26 07:53:20', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (91, '日志导出', 'infra:api-error-log:export', 3, 3, 89, '', '', '', NULL, 0, true, true, true, '', '2021-02-26 07:53:20', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (92, '任务查询', 'infra:job:query', 3, 1, 14, '', '', '', NULL, 0, true, true, true, '1', '2021-03-10 01:26:19', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (93, '日志查询', 'infra:api-access-log:query', 3, 1, 86, '', '', '', NULL, 0, true, true, true, '1', '2021-03-10 01:28:04', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (94, '日志查询', 'infra:api-error-log:query', 3, 1, 89, '', '', '', NULL, 0, true, true, true, '1', '2021-03-10 01:29:09', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (95, '文件列表', '', 2, 5, 193, 'file', 'ep:upload-filled', 'infra/file/index', 'InfraFile', 0, true, true, true, '', '2021-03-12 20:16:20', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (96, '文件查询', 'infra:file:query', 3, 1, 95, '', '', '', NULL, 0, true, true, true, '', '2021-03-12 20:16:20', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (97, '文件删除', 'infra:file:delete', 3, 4, 95, '', '', '', NULL, 0, true, true, true, '', '2021-03-12 20:16:20', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (98, '短信管理', '', 1, 1, 772, 'sms', 'ep:message', NULL, NULL, 0, true, true, true, '1', '2021-04-05 01:10:16', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (99, '短信渠道', '', 2, 0, 98, 'sms-channel', 'fa:stack-exchange', 'system/sms/channel/index', 'SystemSmsChannel', 0, true, true, true, '', '2021-04-01 11:07:15', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (100, '短信渠道查询', 'system:sms-channel:query', 3, 1, 99, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 11:07:15', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (101, '短信渠道创建', 'system:sms-channel:create', 3, 2, 99, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 11:07:15', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (102, '短信渠道更新', 'system:sms-channel:update', 3, 3, 99, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 11:07:15', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (103, '短信渠道删除', 'system:sms-channel:delete', 3, 4, 99, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 11:07:15', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (104, '短信模板', '', 2, 1, 98, 'sms-template', 'ep:connection', 'system/sms/template/index', 'SystemSmsTemplate', 0, true, true, true, '', '2021-04-01 17:35:17', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (105, '短信模板查询', 'system:sms-template:query', 3, 1, 104, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 17:35:17', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (106, '短信模板创建', 'system:sms-template:create', 3, 2, 104, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 17:35:17', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (107, '短信模板更新', 'system:sms-template:update', 3, 3, 104, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 17:35:17', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (108, '短信模板删除', 'system:sms-template:delete', 3, 4, 104, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 17:35:17', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (109, '短信模板导出', 'system:sms-template:export', 3, 5, 104, '', '', '', NULL, 0, true, true, true, '', '2021-04-01 17:35:17', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (110, '发送测试短信', 'system:sms-template:send-sms', 3, 6, 104, '', '', '', NULL, 0, true, true, true, '1', '2021-04-11 00:26:40', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (111, '短信日志', '', 2, 2, 98, 'sms-log', 'fa:edit', 'system/sms/log/index', 'SystemSmsLog', 0, true, true, true, '', '2021-04-11 08:37:05', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (112, '短信日志查询', 'system:sms-log:query', 3, 1, 111, '', '', '', NULL, 0, true, true, true, '', '2021-04-11 08:37:05', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (113, '短信日志导出', 'system:sms-log:export', 3, 5, 111, '', '', '', NULL, 0, true, true, true, '', '2021-04-11 08:37:05', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (129, '租户列表', '', 2, 0, 181, 'list', 'ep:house', 'system/tenant/index', 'SystemTenant', 0, true, true, true, '', '2021-12-14 12:31:43', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (130, '租户查询', 'system:tenant:query', 3, 1, 129, '', '', '', NULL, 0, true, true, true, '', '2021-12-14 12:31:44', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (131, '租户创建', 'system:tenant:create', 3, 2, 129, '', '', '', NULL, 0, true, true, true, '', '2021-12-14 12:31:44', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (132, '租户更新', 'system:tenant:update', 3, 3, 129, '', '', '', NULL, 0, true, true, true, '', '2021-12-14 12:31:44', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (133, '租户删除', 'system:tenant:delete', 3, 4, 129, '', '', '', NULL, 0, true, true, true, '', '2021-12-14 12:31:44', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (134, '租户导出', 'system:tenant:export', 3, 5, 129, '', '', '', NULL, 0, true, true, true, '', '2021-12-14 12:31:44', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (181, '租户管理', '', 2, 0, 1, 'tenant', 'fa-solid:house-user', NULL, NULL, 0, true, true, true, '1', '2022-02-20 01:41:13', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (182, '租户套餐', '', 2, 0, 181, 'package', 'fa:bars', 'system/tenantPackage/index', 'SystemTenantPackage', 0, true, true, true, '', '2022-02-19 17:44:06', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (183, '租户套餐查询', 'system:tenant-package:query', 3, 1, 182, '', '', '', NULL, 0, true, true, true, '', '2022-02-19 17:44:06', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (184, '租户套餐创建', 'system:tenant-package:create', 3, 2, 182, '', '', '', NULL, 0, true, true, true, '', '2022-02-19 17:44:06', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (185, '租户套餐更新', 'system:tenant-package:update', 3, 3, 182, '', '', '', NULL, 0, true, true, true, '', '2022-02-19 17:44:06', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (186, '租户套餐删除', 'system:tenant-package:delete', 3, 4, 182, '', '', '', NULL, 0, true, true, true, '', '2022-02-19 17:44:06', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (187, '文件配置', '', 2, 0, 193, 'file-config', 'fa-solid:file-signature', 'infra/fileConfig/index', 'InfraFileConfig', 0, true, true, true, '', '2022-03-15 14:35:28', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (188, '文件配置查询', 'infra:file-config:query', 3, 1, 187, '', '', '', NULL, 0, true, true, true, '', '2022-03-15 14:35:28', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (189, '文件配置创建', 'infra:file-config:create', 3, 2, 187, '', '', '', NULL, 0, true, true, true, '', '2022-03-15 14:35:28', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (190, '文件配置更新', 'infra:file-config:update', 3, 3, 187, '', '', '', NULL, 0, true, true, true, '', '2022-03-15 14:35:28', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (191, '文件配置删除', 'infra:file-config:delete', 3, 4, 187, '', '', '', NULL, 0, true, true, true, '', '2022-03-15 14:35:28', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (192, '文件配置导出', 'infra:file-config:export', 3, 5, 187, '', '', '', NULL, 0, true, true, true, '', '2022-03-15 14:35:28', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (193, '文件管理', '', 2, 6, 2, 'file', 'ep:files', NULL, '', 0, true, true, true, '1', '2022-03-16 23:47:40', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (195, '数据源配置', '', 2, 1, 2, 'data-source-config', 'ep:data-analysis', 'infra/dataSourceConfig/index', 'InfraDataSourceConfig', 0, true, true, true, '', '2022-04-27 14:37:32', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (196, '数据源配置查询', 'infra:data-source-config:query', 3, 1, 195, '', '', '', NULL, 0, true, true, true, '', '2022-04-27 14:37:32', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (197, '数据源配置创建', 'infra:data-source-config:create', 3, 2, 195, '', '', '', NULL, 0, true, true, true, '', '2022-04-27 14:37:32', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (198, '数据源配置更新', 'infra:data-source-config:update', 3, 3, 195, '', '', '', NULL, 0, true, true, true, '', '2022-04-27 14:37:32', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (199, '数据源配置删除', 'infra:data-source-config:delete', 3, 4, 195, '', '', '', NULL, 0, true, true, true, '', '2022-04-27 14:37:32', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (200, '数据源配置导出', 'infra:data-source-config:export', 3, 5, 195, '', '', '', NULL, 0, true, true, true, '', '2022-04-27 14:37:32', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (201, 'OAuth 2.0', '', 2, 10, 1, 'oauth2', 'fa:dashcube', NULL, NULL, 0, true, true, true, '1', '2022-05-09 23:38:17', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (202, '应用管理', '', 2, 0, 201, 'oauth2/application', 'fa:hdd-o', 'system/oauth2/client/index', 'SystemOAuth2Client', 0, true, true, true, '', '2022-05-10 16:26:33', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (203, '客户端查询', 'system:oauth2-client:query', 3, 1, 202, '', '', '', NULL, 0, true, true, true, '', '2022-05-10 16:26:33', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (204, '客户端创建', 'system:oauth2-client:create', 3, 2, 202, '', '', '', NULL, 0, true, true, true, '', '2022-05-10 16:26:33', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (205, '客户端更新', 'system:oauth2-client:update', 3, 3, 202, '', '', '', NULL, 0, true, true, true, '', '2022-05-10 16:26:33', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (206, '客户端删除', 'system:oauth2-client:delete', 3, 4, 202, '', '', '', NULL, 0, true, true, true, '', '2022-05-10 16:26:33', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (271, '地区管理', '', 2, 14, 1, 'area', 'fa:map-marker', 'system/area/index', 'SystemArea', 0, true, true, true, '1', '2022-12-23 17:35:05', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (318, '邮箱管理', '', 2, 2, 772, 'mail', 'fa-solid:mail-bulk', NULL, NULL, 0, true, true, true, '1', '2023-01-25 17:27:44', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (319, '邮箱账号', '', 2, 0, 318, 'mail-account', 'fa:universal-access', 'system/mail/account/index', 'SystemMailAccount', 0, true, true, true, '', '2023-01-25 09:33:48', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (320, '账号查询', 'system:mail-account:query', 3, 1, 319, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 09:33:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (321, '账号创建', 'system:mail-account:create', 3, 2, 319, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 09:33:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (322, '账号更新', 'system:mail-account:update', 3, 3, 319, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 09:33:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (323, '账号删除', 'system:mail-account:delete', 3, 4, 319, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 09:33:48', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (324, '邮件模版', '', 2, 0, 318, 'mail-template', 'fa:tag', 'system/mail/template/index', 'SystemMailTemplate', 0, true, true, true, '', '2023-01-25 12:05:31', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (325, '模版查询', 'system:mail-template:query', 3, 1, 324, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 12:05:31', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (326, '模版创建', 'system:mail-template:create', 3, 2, 324, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 12:05:31', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (327, '模版更新', 'system:mail-template:update', 3, 3, 324, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 12:05:31', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (328, '模版删除', 'system:mail-template:delete', 3, 4, 324, '', '', '', NULL, 0, true, true, true, '', '2023-01-25 12:05:31', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (329, '邮件记录', '', 2, 0, 318, 'mail-log', 'fa:edit', 'system/mail/log/index', 'SystemMailLog', 0, true, true, true, '', '2023-01-26 02:16:50', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (330, '日志查询', 'system:mail-log:query', 3, 1, 329, '', '', '', NULL, 0, true, true, true, '', '2023-01-26 02:16:50', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (331, '发送测试邮件', 'system:mail-template:send-mail', 3, 5, 324, '', '', '', NULL, 0, true, true, true, '1', '2023-01-26 23:29:15', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (332, '站内信管理', '', 1, 3, 772, 'notify', 'ep:message-box', NULL, NULL, 0, true, true, true, '1', '2023-01-28 10:25:18', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (333, '模板管理', '', 2, 0, 332, 'notify-template', 'fa:archive', 'system/notify/template/index', 'SystemNotifyTemplate', 0, true, true, true, '', '2023-01-28 02:26:42', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (334, '站内信模板查询', 'system:notify-template:query', 3, 1, 333, '', '', '', NULL, 0, true, true, true, '', '2023-01-28 02:26:42', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (335, '站内信模板创建', 'system:notify-template:create', 3, 2, 333, '', '', '', NULL, 0, true, true, true, '', '2023-01-28 02:26:42', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (336, '站内信模板更新', 'system:notify-template:update', 3, 3, 333, '', '', '', NULL, 0, true, true, true, '', '2023-01-28 02:26:42', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (337, '站内信模板删除', 'system:notify-template:delete', 3, 4, 333, '', '', '', NULL, 0, true, true, true, '', '2023-01-28 02:26:42', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (338, '发送测试站内信', 'system:notify-template:send-notify', 3, 5, 333, '', '', '', NULL, 0, true, true, true, '1', '2023-01-28 10:54:43', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (339, '消息记录', '', 2, 0, 332, 'notify-message', 'fa:edit', 'system/notify/message/index', 'SystemNotifyMessage', 0, true, true, true, '', '2023-01-28 04:28:22', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (340, '站内信消息查询', 'system:notify-message:query', 3, 1, 339, '', '', '', NULL, 0, true, true, true, '', '2023-01-28 04:28:22', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (529, '三方登录', '', 1, 10, 1, 'social', 'fa:rocket', '', '', 0, true, true, true, '1', '2023-11-04 12:12:01', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (530, '三方应用', '', 2, 1, 529, 'client', 'ep:set-up', 'system/social/client/index.vue', 'SocialClient', 0, true, true, true, '1', '2023-11-04 12:17:19', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (531, '三方应用查询', 'system:social-client:query', 3, 1, 530, '', '', '', '', 0, true, true, true, '1', '2023-11-04 12:43:12', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (532, '三方应用创建', 'system:social-client:create', 3, 2, 530, '', '', '', '', 0, true, true, true, '1', '2023-11-04 12:43:58', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (533, '三方应用更新', 'system:social-client:update', 3, 3, 530, '', '', '', '', 0, true, true, true, '1', '2023-11-04 12:44:27', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (534, '三方应用删除', 'system:social-client:delete', 3, 4, 530, '', '', '', '', 0, true, true, true, '1', '2023-11-04 12:44:43', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (535, '三方用户', 'system:social-user:query', 2, 2, 529, 'user', 'ep:avatar', 'system/social/user/index.vue', 'SocialUser', 0, true, true, true, '1', '2023-11-04 14:01:05', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (536, '主子表（内嵌）', '', 2, 12, 83, 'demo03-inner', 'fa:power-off', 'infra/demo/demo03/inner/index', 'Demo03StudentInner', 0, true, true, true, '', '2023-11-13 04:39:51', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (537, '单表（增删改查）', '', 2, 1, 83, 'demo01-contact', 'ep:bicycle', 'infra/demo/demo01/index', 'Demo01Contact', 0, true, true, true, '', '2023-11-15 14:42:30', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (538, '示例联系人查询', 'infra:demo01-contact:query', 3, 1, 537, '', '', '', NULL, 0, true, true, true, '', '2023-11-15 14:42:30', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (539, '示例联系人创建', 'infra:demo01-contact:create', 3, 2, 537, '', '', '', NULL, 0, true, true, true, '', '2023-11-15 14:42:30', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (540, '示例联系人更新', 'infra:demo01-contact:update', 3, 3, 537, '', '', '', NULL, 0, true, true, true, '', '2023-11-15 14:42:30', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (541, '示例联系人删除', 'infra:demo01-contact:delete', 3, 4, 537, '', '', '', NULL, 0, true, true, true, '', '2023-11-15 14:42:30', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (542, '示例联系人导出', 'infra:demo01-contact:export', 3, 5, 537, '', '', '', NULL, 0, true, true, true, '', '2023-11-15 14:42:30', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (543, '树表（增删改查）', '', 2, 2, 83, 'demo02-category', 'fa:tree', 'infra/demo/demo02/index', 'Demo02Category', 0, true, true, true, '', '2023-11-16 12:18:27', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (544, '示例分类查询', 'infra:demo02-category:query', 3, 1, 543, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:18:27', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (545, '示例分类创建', 'infra:demo02-category:create', 3, 2, 543, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:18:27', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (546, '示例分类更新', 'infra:demo02-category:update', 3, 3, 543, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:18:27', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (547, '示例分类删除', 'infra:demo02-category:delete', 3, 4, 543, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:18:27', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (548, '示例分类导出', 'infra:demo02-category:export', 3, 5, 543, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:18:27', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (549, '主子表（标准）', '', 2, 10, 83, 'demo03-normal', 'fa:battery-3', 'infra/demo/demo03/normal/index', 'Demo03StudentNormal', 0, true, true, true, '', '2023-11-16 12:53:37', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (550, '学生查询', 'infra:demo03-student:query', 3, 1, 549, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:53:37', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (551, '学生创建', 'infra:demo03-student:create', 3, 2, 549, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:53:37', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (552, '学生更新', 'infra:demo03-student:update', 3, 3, 549, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:53:37', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (553, '学生删除', 'infra:demo03-student:delete', 3, 4, 549, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:53:37', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (554, '学生导出', 'infra:demo03-student:export', 3, 5, 549, '', '', '', NULL, 0, true, true, true, '', '2023-11-16 12:53:37', '', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (555, '主子表（ERP）', '', 2, 11, 83, 'demo03-erp', 'ep:calendar', 'infra/demo/demo03/erp/index', 'Demo03StudentERP', 0, true, true, true, '', '2023-11-16 15:50:59', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (565, 'WebSocket', '', 2, 5, 2, 'websocket', 'ep:connection', 'infra/webSocket/index', 'InfraWebSocket', 0, true, true, true, '1', '2023-11-23 19:41:55', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (772, '消息中心', '', 1, 7, 1, 'messages', 'ep:chat-dot-round', '', '', 0, true, true, true, '1', '2024-04-22 23:54:30', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (773, '监控中心', '', 1, 10, 2, 'monitors', 'ep:monitor', '', '', 0, true, true, true, '1', '2024-04-23 00:04:44', '1', '2026-08-14 01:49:42', 0);
INSERT INTO public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (920, '租户切换', 'system:tenant:visit', 3, 999, 129, '', '', '', '', 0, true, true, true, '1', '2025-05-05 15:25:32', '1', '2026-08-14 01:49:42', 0);


--
-- Data for Name: system_notice; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_notify_message; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_notify_template; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_oauth2_access_token; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_oauth2_approve; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_oauth2_client; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_oauth2_client (id, client_id, secret, name, logo, description, status, access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes, authorities, resource_ids, additional_information, creator, create_time, updater, update_time, deleted) VALUES (1, 'default', 'admin123', '芋道源码', 'http://test.yudao.iocoder.cn/20250502/sort2_1746189740718.png', '我是描述', 0, 1800, 2592000, '["https://www.iocoder.cn","https://doc.iocoder.cn"]', '["password","authorization_code","implicit","refresh_token","client_credentials"]', '["user.read","user.write"]', '[]', '["user.read","user.write"]', '[]', '{}', '1', '2022-05-11 21:47:12', '1', '2025-12-07 20:07:09', 0);


--
-- Data for Name: system_oauth2_code; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_oauth2_refresh_token; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_operate_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_post; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_role; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, '超级管理员', 'super_admin', 1, 1, '', 0, 1, '超级管理员', 'admin', '2021-01-05 17:03:48', '', '2022-02-22 05:08:21', 0, 1);


--
-- Data for Name: system_role_menu; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9100, 1, 1, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9101, 1, 2, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9102, 1, 4, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9103, 1, 5, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9104, 1, 6, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9105, 1, 7, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9106, 1, 8, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9107, 1, 9, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9108, 1, 10, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9109, 1, 11, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9110, 1, 12, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9111, 1, 13, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9112, 1, 14, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9113, 1, 15, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9114, 1, 16, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9115, 1, 17, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9116, 1, 18, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9117, 1, 19, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9118, 1, 20, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9119, 1, 21, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9120, 1, 22, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9121, 1, 23, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9122, 1, 24, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9123, 1, 25, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9124, 1, 26, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9125, 1, 27, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9126, 1, 28, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9127, 1, 29, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9128, 1, 30, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9129, 1, 31, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9130, 1, 32, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9131, 1, 33, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9132, 1, 34, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9133, 1, 35, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9134, 1, 36, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9135, 1, 37, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9136, 1, 38, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9137, 1, 39, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9138, 1, 40, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9139, 1, 41, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9140, 1, 42, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9141, 1, 43, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9142, 1, 44, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9143, 1, 45, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9144, 1, 46, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9145, 1, 47, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9146, 1, 48, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9147, 1, 49, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9148, 1, 50, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9149, 1, 51, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9150, 1, 52, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9151, 1, 53, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9152, 1, 54, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9153, 1, 55, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9154, 1, 56, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9155, 1, 57, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9156, 1, 58, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9157, 1, 59, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9158, 1, 60, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9159, 1, 61, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9160, 1, 62, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9161, 1, 63, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9162, 1, 64, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9163, 1, 65, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9164, 1, 66, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9165, 1, 67, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9166, 1, 68, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9167, 1, 69, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9168, 1, 70, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9169, 1, 71, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9170, 1, 72, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9171, 1, 73, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9172, 1, 74, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9173, 1, 75, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9174, 1, 76, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9175, 1, 77, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9176, 1, 78, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9177, 1, 79, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9178, 1, 80, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9179, 1, 81, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9180, 1, 82, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9181, 1, 83, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9182, 1, 84, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9183, 1, 85, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9184, 1, 86, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9185, 1, 87, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9186, 1, 88, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9187, 1, 89, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9188, 1, 90, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9189, 1, 91, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9190, 1, 92, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9191, 1, 93, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9192, 1, 94, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9193, 1, 95, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9194, 1, 96, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9195, 1, 97, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9196, 1, 98, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9197, 1, 99, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9198, 1, 100, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9199, 1, 101, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9200, 1, 102, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9201, 1, 103, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9202, 1, 104, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9203, 1, 105, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9204, 1, 106, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9205, 1, 107, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9206, 1, 108, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9207, 1, 109, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9208, 1, 110, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9209, 1, 111, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9210, 1, 112, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9211, 1, 113, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9212, 1, 129, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9213, 1, 130, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9214, 1, 131, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9215, 1, 132, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9216, 1, 133, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9217, 1, 134, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9218, 1, 181, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9219, 1, 182, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9220, 1, 183, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9221, 1, 184, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9222, 1, 185, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9223, 1, 186, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9224, 1, 187, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9225, 1, 188, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9226, 1, 189, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9227, 1, 190, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9228, 1, 191, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9229, 1, 192, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9230, 1, 193, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9231, 1, 195, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9232, 1, 196, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9233, 1, 197, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9234, 1, 198, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9235, 1, 199, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9236, 1, 200, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9237, 1, 201, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9238, 1, 202, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9239, 1, 203, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9240, 1, 204, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9241, 1, 205, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9242, 1, 206, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9243, 1, 271, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9244, 1, 318, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9245, 1, 319, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9246, 1, 320, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9247, 1, 321, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9248, 1, 322, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9249, 1, 323, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9250, 1, 324, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9251, 1, 325, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9252, 1, 326, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9253, 1, 327, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9254, 1, 328, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9255, 1, 329, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9256, 1, 330, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9257, 1, 331, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9258, 1, 332, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9259, 1, 333, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9260, 1, 334, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9261, 1, 335, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9262, 1, 336, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9263, 1, 337, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9264, 1, 338, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9265, 1, 339, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9266, 1, 340, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9267, 1, 529, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9268, 1, 530, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9269, 1, 531, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9270, 1, 532, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9271, 1, 533, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9272, 1, 534, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9273, 1, 535, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9274, 1, 536, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9275, 1, 537, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9276, 1, 538, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9277, 1, 539, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9278, 1, 540, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9279, 1, 541, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9280, 1, 542, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9281, 1, 543, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9282, 1, 544, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9283, 1, 545, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9284, 1, 546, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9285, 1, 547, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9286, 1, 548, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9287, 1, 549, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9288, 1, 550, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9289, 1, 551, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9290, 1, 552, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9291, 1, 553, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9292, 1, 554, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9293, 1, 555, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9294, 1, 565, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9295, 1, 772, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9296, 1, 773, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);
INSERT INTO public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (9297, 1, 920, '1', '2026-11-12 12:13:15.966342', '1', '2026-11-12 12:13:15.966342', 0, 1);


--
-- Data for Name: system_sms_channel; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_sms_code; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_sms_log; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_sms_template; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_social_client; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_social_user; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_social_user_bind; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_tenant; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_tenant (id, name, contact_user_id, contact_name, contact_mobile, status, websites, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted) VALUES (1, '芋道源码', NULL, '芋艿', '17321315478', 0, 'www.iocoder.cn,127.0.0.1:3000,wxc4598c446f8a9cb3', 111, '2099-02-19 17:14:16', 1, '1', '2021-01-05 17:03:47', '1', '2025-08-19 05:18:41', 0);


--
-- Data for Name: system_tenant_package; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_tenant_package (id, name, status, remark, menu_ids, creator, create_time, updater, update_time, deleted) VALUES (111, '普通套餐', 0, '小功能', '[1,2,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101,102,103,104,105,106,107,108,109,110,111,112,113,129,130,131,132,133,134,181,182,183,184,185,186,187,188,189,190,191,192,193,195,196,197,198,199,200,201,202,203,204,205,206,271,318,319,320,321,322,323,324,325,326,327,328,329,330,331,332,333,334,335,336,337,338,339,340,529,530,531,532,533,534,535,536,537,538,539,540,541,542,543,544,545,546,547,548,549,550,551,552,553,554,555,565,772,773,920]', '1', '2022-02-22 00:54:00', '1', '2026-08-14 01:49:42', 0);


--
-- Data for Name: system_user_post; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: system_user_role; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, 1, 1, '', '2022-01-11 13:19:45', '', '2022-05-12 12:35:17', 0, 1);


--
-- Data for Name: system_users; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id) VALUES (1, 'admin', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '芋道源码', '管理员', 103, '[]', '13aoteman@126.com', '18818260272', 1, 'http://test.yudao.iocoder.cn/20260517/blob_1778998103688.png', 0, '0:0:0:0:0:0:0:1', '2026-09-19 15:27:56', 'admin', '2021-01-05 17:03:47', NULL, '2026-09-19 15:27:56', 0, 1);


--
-- Name: infra_api_access_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_api_access_log_seq', 1, false);


--
-- Name: infra_api_error_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_api_error_log_seq', 1, false);


--
-- Name: infra_codegen_column_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_codegen_column_seq', 1, false);


--
-- Name: infra_codegen_table_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_codegen_table_seq', 1, false);


--
-- Name: infra_config_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_config_seq', 14, false);


--
-- Name: infra_data_source_config_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_data_source_config_seq', 1, false);


--
-- Name: infra_file_config_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_file_config_seq', 36, false);


--
-- Name: infra_file_content_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_file_content_seq', 1, false);


--
-- Name: infra_file_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_file_seq', 1, false);


--
-- Name: infra_job_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_job_log_seq', 1, false);


--
-- Name: infra_job_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_job_seq', 12909, false);


--
-- Name: system_dept_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_dept_seq', 118, false);


--
-- Name: system_dict_data_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_dict_data_seq', 1635, false);


--
-- Name: system_dict_type_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_dict_type_seq', 353, false);


--
-- Name: system_login_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_login_log_seq', 1, false);


--
-- Name: system_mail_account_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_mail_account_seq', 5, false);


--
-- Name: system_mail_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_mail_log_seq', 1, false);


--
-- Name: system_mail_template_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_mail_template_seq', 16, false);


--
-- Name: system_menu_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_menu_seq', 12940, false);


--
-- Name: system_notice_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_notice_seq', 5, false);


--
-- Name: system_notify_message_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_notify_message_seq', 97, false);


--
-- Name: system_notify_template_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_notify_template_seq', 1, false);


--
-- Name: system_oauth2_access_token_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_access_token_seq', 1, false);


--
-- Name: system_oauth2_approve_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_approve_seq', 1, false);


--
-- Name: system_oauth2_client_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_client_seq', 43, false);


--
-- Name: system_oauth2_code_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_code_seq', 1, false);


--
-- Name: system_oauth2_refresh_token_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_refresh_token_seq', 1, false);


--
-- Name: system_operate_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_operate_log_seq', 1, false);


--
-- Name: system_post_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_post_seq', 8, false);


--
-- Name: system_role_menu_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_role_menu_seq', 9297, true);


--
-- Name: system_role_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_role_seq', 163, false);


--
-- Name: system_sms_channel_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_channel_seq', 8, false);


--
-- Name: system_sms_code_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_code_seq', 1, false);


--
-- Name: system_sms_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_log_seq', 1, false);


--
-- Name: system_sms_template_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_template_seq', 20, false);


--
-- Name: system_social_client_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_social_client_seq', 48, false);


--
-- Name: system_social_user_bind_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_social_user_bind_seq', 1, false);


--
-- Name: system_social_user_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_social_user_seq', 1, false);


--
-- Name: system_tenant_package_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_tenant_package_seq', 114, false);


--
-- Name: system_tenant_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_tenant_seq', 123, false);


--
-- Name: system_user_post_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_user_post_seq', 130, false);


--
-- Name: system_user_role_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_user_role_seq', 84, false);


--
-- Name: system_users_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_users_seq', 241, false);


--
-- Name: infra_api_access_log pk_infra_api_access_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_api_access_log
    ADD CONSTRAINT pk_infra_api_access_log PRIMARY KEY (id);


--
-- Name: infra_api_error_log pk_infra_api_error_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_api_error_log
    ADD CONSTRAINT pk_infra_api_error_log PRIMARY KEY (id);


--
-- Name: infra_codegen_column pk_infra_codegen_column; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_codegen_column
    ADD CONSTRAINT pk_infra_codegen_column PRIMARY KEY (id);


--
-- Name: infra_codegen_table pk_infra_codegen_table; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_codegen_table
    ADD CONSTRAINT pk_infra_codegen_table PRIMARY KEY (id);


--
-- Name: infra_config pk_infra_config; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_config
    ADD CONSTRAINT pk_infra_config PRIMARY KEY (id);


--
-- Name: infra_data_source_config pk_infra_data_source_config; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_data_source_config
    ADD CONSTRAINT pk_infra_data_source_config PRIMARY KEY (id);


--
-- Name: infra_file pk_infra_file; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_file
    ADD CONSTRAINT pk_infra_file PRIMARY KEY (id);


--
-- Name: infra_file_config pk_infra_file_config; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_file_config
    ADD CONSTRAINT pk_infra_file_config PRIMARY KEY (id);


--
-- Name: infra_file_content pk_infra_file_content; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_file_content
    ADD CONSTRAINT pk_infra_file_content PRIMARY KEY (id);


--
-- Name: infra_job pk_infra_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_job
    ADD CONSTRAINT pk_infra_job PRIMARY KEY (id);


--
-- Name: infra_job_log pk_infra_job_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_job_log
    ADD CONSTRAINT pk_infra_job_log PRIMARY KEY (id);


--
-- Name: system_dept pk_system_dept; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dept
    ADD CONSTRAINT pk_system_dept PRIMARY KEY (id);


--
-- Name: system_dict_data pk_system_dict_data; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_data
    ADD CONSTRAINT pk_system_dict_data PRIMARY KEY (id);


--
-- Name: system_dict_type pk_system_dict_type; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_type
    ADD CONSTRAINT pk_system_dict_type PRIMARY KEY (id);


--
-- Name: system_login_log pk_system_login_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_login_log
    ADD CONSTRAINT pk_system_login_log PRIMARY KEY (id);


--
-- Name: system_mail_account pk_system_mail_account; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mail_account
    ADD CONSTRAINT pk_system_mail_account PRIMARY KEY (id);


--
-- Name: system_mail_log pk_system_mail_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mail_log
    ADD CONSTRAINT pk_system_mail_log PRIMARY KEY (id);


--
-- Name: system_mail_template pk_system_mail_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mail_template
    ADD CONSTRAINT pk_system_mail_template PRIMARY KEY (id);


--
-- Name: system_menu pk_system_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_menu
    ADD CONSTRAINT pk_system_menu PRIMARY KEY (id);


--
-- Name: system_notice pk_system_notice; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notice
    ADD CONSTRAINT pk_system_notice PRIMARY KEY (id);


--
-- Name: system_notify_message pk_system_notify_message; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notify_message
    ADD CONSTRAINT pk_system_notify_message PRIMARY KEY (id);


--
-- Name: system_notify_template pk_system_notify_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notify_template
    ADD CONSTRAINT pk_system_notify_template PRIMARY KEY (id);


--
-- Name: system_oauth2_access_token pk_system_oauth2_access_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_access_token
    ADD CONSTRAINT pk_system_oauth2_access_token PRIMARY KEY (id);


--
-- Name: system_oauth2_approve pk_system_oauth2_approve; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_approve
    ADD CONSTRAINT pk_system_oauth2_approve PRIMARY KEY (id);


--
-- Name: system_oauth2_client pk_system_oauth2_client; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_client
    ADD CONSTRAINT pk_system_oauth2_client PRIMARY KEY (id);


--
-- Name: system_oauth2_code pk_system_oauth2_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_code
    ADD CONSTRAINT pk_system_oauth2_code PRIMARY KEY (id);


--
-- Name: system_oauth2_refresh_token pk_system_oauth2_refresh_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_refresh_token
    ADD CONSTRAINT pk_system_oauth2_refresh_token PRIMARY KEY (id);


--
-- Name: system_operate_log pk_system_operate_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_operate_log
    ADD CONSTRAINT pk_system_operate_log PRIMARY KEY (id);


--
-- Name: system_post pk_system_post; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_post
    ADD CONSTRAINT pk_system_post PRIMARY KEY (id);


--
-- Name: system_role pk_system_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_role
    ADD CONSTRAINT pk_system_role PRIMARY KEY (id);


--
-- Name: system_role_menu pk_system_role_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_role_menu
    ADD CONSTRAINT pk_system_role_menu PRIMARY KEY (id);


--
-- Name: system_sms_channel pk_system_sms_channel; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_channel
    ADD CONSTRAINT pk_system_sms_channel PRIMARY KEY (id);


--
-- Name: system_sms_code pk_system_sms_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_code
    ADD CONSTRAINT pk_system_sms_code PRIMARY KEY (id);


--
-- Name: system_sms_log pk_system_sms_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_log
    ADD CONSTRAINT pk_system_sms_log PRIMARY KEY (id);


--
-- Name: system_sms_template pk_system_sms_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_template
    ADD CONSTRAINT pk_system_sms_template PRIMARY KEY (id);


--
-- Name: system_social_client pk_system_social_client; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_client
    ADD CONSTRAINT pk_system_social_client PRIMARY KEY (id);


--
-- Name: system_social_user pk_system_social_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_user
    ADD CONSTRAINT pk_system_social_user PRIMARY KEY (id);


--
-- Name: system_social_user_bind pk_system_social_user_bind; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_user_bind
    ADD CONSTRAINT pk_system_social_user_bind PRIMARY KEY (id);


--
-- Name: system_tenant pk_system_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_tenant
    ADD CONSTRAINT pk_system_tenant PRIMARY KEY (id);


--
-- Name: system_tenant_package pk_system_tenant_package; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_tenant_package
    ADD CONSTRAINT pk_system_tenant_package PRIMARY KEY (id);


--
-- Name: system_user_post pk_system_user_post; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_post
    ADD CONSTRAINT pk_system_user_post PRIMARY KEY (id);


--
-- Name: system_user_role pk_system_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_role
    ADD CONSTRAINT pk_system_user_role PRIMARY KEY (id);


--
-- Name: system_users pk_system_users; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_users
    ADD CONSTRAINT pk_system_users PRIMARY KEY (id);


--
-- Name: idx_infra_api_access_log_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_api_access_log_01 ON public.infra_api_access_log USING btree (create_time);


--
-- Name: idx_infra_api_error_log_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_api_error_log_01 ON public.infra_api_error_log USING btree (create_time);


--
-- Name: idx_infra_codegen_column_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_codegen_column_01 ON public.infra_codegen_column USING btree (table_id);


--
-- Name: idx_infra_config_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_config_01 ON public.infra_config USING btree (config_key);


--
-- Name: idx_infra_file_content_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_file_content_01 ON public.infra_file_content USING btree (config_id, path);


--
-- Name: idx_infra_job_log_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_job_log_01 ON public.infra_job_log USING btree (job_id);


--
-- Name: idx_infra_job_log_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_job_log_02 ON public.infra_job_log USING btree (create_time);


--
-- Name: idx_system_login_log_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_login_log_01 ON public.system_login_log USING btree (username);


--
-- Name: idx_system_login_log_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_login_log_02 ON public.system_login_log USING btree (create_time);


--
-- Name: idx_system_notify_message_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_notify_message_01 ON public.system_notify_message USING btree (user_id, user_type, read_status);


--
-- Name: idx_system_oauth2_access_token_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_access_token_01 ON public.system_oauth2_access_token USING btree (access_token);


--
-- Name: idx_system_oauth2_access_token_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_access_token_02 ON public.system_oauth2_access_token USING btree (refresh_token);


--
-- Name: idx_system_oauth2_approve_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_approve_01 ON public.system_oauth2_approve USING btree (user_id, user_type, client_id);


--
-- Name: idx_system_oauth2_client_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_client_01 ON public.system_oauth2_client USING btree (client_id);


--
-- Name: idx_system_oauth2_code_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_code_01 ON public.system_oauth2_code USING btree (code);


--
-- Name: idx_system_oauth2_refresh_token_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_refresh_token_01 ON public.system_oauth2_refresh_token USING btree (refresh_token);


--
-- Name: idx_system_operate_log_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_operate_log_01 ON public.system_operate_log USING btree (user_id);


--
-- Name: idx_system_operate_log_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_operate_log_02 ON public.system_operate_log USING btree (create_time);


--
-- Name: idx_system_role_menu_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_role_menu_01 ON public.system_role_menu USING btree (role_id);


--
-- Name: idx_system_sms_code_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_sms_code_01 ON public.system_sms_code USING btree (mobile);


--
-- Name: idx_system_social_user_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_social_user_01 ON public.system_social_user USING btree (type, openid);


--
-- Name: idx_system_social_user_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_social_user_02 ON public.system_social_user USING btree (type, code, state);


--
-- Name: idx_system_social_user_bind_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_social_user_bind_01 ON public.system_social_user_bind USING btree (user_type, social_user_id);


--
-- Name: idx_system_user_role_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_user_role_01 ON public.system_user_role USING btree (user_id);


--
-- Name: idx_system_users_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_users_01 ON public.system_users USING btree (username);


--
-- Name: idx_system_users_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_users_02 ON public.system_users USING btree (mobile);


--
-- Name: idx_system_users_03; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_users_03 ON public.system_users USING btree (email);


--
-- Name: idx_system_users_04; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_users_04 ON public.system_users USING btree (dept_id);


--
-- PostgreSQL database dump complete
--


