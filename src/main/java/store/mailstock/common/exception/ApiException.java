package store.mailstock.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    public ApiException(HttpStatus s, String msg) { super(msg); this.status = s; }
    public HttpStatus getStatus() { return status; }

    public static ApiException notFound(String m) { return new ApiException(HttpStatus.NOT_FOUND, m); }
    public static ApiException badRequest(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
    public static ApiException forbidden(String m) { return new ApiException(HttpStatus.FORBIDDEN, m); }
    public static ApiException conflict(String m) { return new ApiException(HttpStatus.CONFLICT, m); }
    public static ApiException unauthorized(String m) { return new ApiException(HttpStatus.UNAUTHORIZED, m); }
}
