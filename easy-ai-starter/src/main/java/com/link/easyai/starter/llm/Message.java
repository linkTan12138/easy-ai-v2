package com.link.easyai.starter.llm;

/**
 * 对话消息实体。
 * 用于 LLMProvider 接口中传递多轮对话上下文。
 */
public class Message {

    /** 角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /** 创建 system 角色消息 */
    public static Message system(String content) {
        return new Message("system", content);
    }

    /** 创建 user 角色消息 */
    public static Message user(String content) {
        return new Message("user", content);
    }

    /** 创建 assistant 角色消息 */
    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "Message{role='" + role + "', content='" + content + "'}";
    }
}
