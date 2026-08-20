package com.barbearia.shared.validacao;

/**
 * Normalizacao e validacao de CPF (apenas o digito verificador — nao
 * consulta a Receita Federal). Reaproveitavel por qualquer feature que
 * precise validar CPF de cliente, profissional ou usuario.
 */
public final class CpfValidador {

    private CpfValidador() {
    }

    /** Remove tudo que nao for digito. */
    public static String normalizar(String cpf) {
        if (cpf == null) {
            return null;
        }
        return cpf.replaceAll("\\D", "");
    }

    /** Valida os digitos verificadores de um CPF ja normalizado (11 digitos). */
    public static boolean valido(String cpf) {
        if (cpf == null || cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        if (!cpf.chars().allMatch(Character::isDigit)) {
            return false;
        }

        int[] digitos = cpf.chars().map(c -> c - '0').toArray();

        return digitos[9] == calcularDigitoVerificador(digitos, 9)
                && digitos[10] == calcularDigitoVerificador(digitos, 10);
    }

    private static int calcularDigitoVerificador(int[] digitos, int quantidade) {
        int peso = quantidade + 1;
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += digitos[i] * peso--;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
