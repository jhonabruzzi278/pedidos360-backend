package cl.pedidos360.bff;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Un cuerpo que el BFF mismo no puede leer o validar: 400 con el cuerpo de error unico y texto fijo. */
@RestControllerAdvice
class RequestErrorHandler {
  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<String> invalidRequest() {
    return ApiError.response(HttpStatus.BAD_REQUEST, "bad_request", "La solicitud no es valida");
  }
}
