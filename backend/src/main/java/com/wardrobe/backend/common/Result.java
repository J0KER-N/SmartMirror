package com.wardrobe.backend.common; // ⚠️ 确保包名正确

public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // 成功快捷方法
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    // 失败快捷方法
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    // 必须有 Getter，否则返回给前端的 JSON 数据会是空的
    public int getCode() { return code; }
    public String getMsg() { return msg; }
    public T getData() { return data; }
}
