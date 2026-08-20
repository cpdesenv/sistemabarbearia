package com.barbearia.shared.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(MethodArgumentNotValidException ex, WebRequest request) {
        List<ErroCampo> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(this::paraErroCampo)
                .toList();
        ErroResposta corpo = ErroResposta.deValidacao("Um ou mais campos sao invalidos.", caminho(request), campos);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResposta> tratarCredenciaisInvalidas(CredenciaisInvalidasException ex,
            WebRequest request) {
        ErroResposta corpo = ErroResposta.de(401, "CREDENCIAIS_INVALIDAS", ex.getMessage(), caminho(request));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(corpo);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException ex,
            WebRequest request) {
        ErroResposta corpo = ErroResposta.de(404, "RECURSO_NAO_ENCONTRADO", ex.getMessage(), caminho(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> tratarAcessoNegado(AccessDeniedException ex, WebRequest request) {
        ErroResposta corpo = ErroResposta.de(403, "ACESSO_NEGADO",
                "Voce nao tem permissao para acessar este recurso.", caminho(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(corpo);
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroResposta> tratarNegocio(NegocioException ex, WebRequest request) {
        ErroResposta corpo = ErroResposta.de(400, "REGRA_DE_NEGOCIO", ex.getMessage(), caminho(request));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarErroInesperado(Exception ex, WebRequest request) {
        log.error("Erro inesperado processando {}", caminho(request), ex);
        ErroResposta corpo = ErroResposta.de(500, "ERRO_INTERNO", "Ocorreu um erro inesperado. Tente novamente.",
                caminho(request));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo);
    }

    private ErroCampo paraErroCampo(FieldError erro) {
        return new ErroCampo(erro.getField(), erro.getDefaultMessage());
    }

    private String caminho(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
