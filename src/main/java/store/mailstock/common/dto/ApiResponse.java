package store.mailstock.common.dto;

public record ApiResponse<T>(boolean success, String message, T data) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, "ok", data); }
    public static <T> ApiResponse<T> ok(String msg, T data) { return new ApiResponse<>(true, msg, data); }
    public static <T> ApiResponse<T> fail(String msg) { return new ApiResponse<>(false, msg, null); }
}
