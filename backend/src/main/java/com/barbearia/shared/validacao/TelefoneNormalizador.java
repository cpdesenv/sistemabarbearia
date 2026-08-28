package com.barbearia.shared.validacao;

/**
 * Normaliza numeros de telefone brasileiros para E.164 (+55DDDNUMERO).
 * Formato canonico de telefone usado em todo o sistema (cadastro de
 * cliente, autoagendamento), entao todo telefone armazenado passa por aqui.
 */
public final class TelefoneNormalizador {

    private TelefoneNormalizador() {
    }

    /**
     * @return o telefone em E.164 (ex.: "+5519999998888") ou {@code null} se a
     *         entrada for nula/vazia.
     * @throws IllegalArgumentException se a entrada nao puder ser reconhecida
     *                                   como um telefone brasileiro valido.
     */
    public static String normalizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }

        String digitos = bruto.replaceAll("\\D", "");

        if (digitos.startsWith("55") && (digitos.length() == 12 || digitos.length() == 13)) {
            digitos = digitos.substring(2);
        }

        if (digitos.length() != 10 && digitos.length() != 11) {
            throw new IllegalArgumentException("Telefone invalido.");
        }

        String ddd = digitos.substring(0, 2);
        if (Integer.parseInt(ddd) < 11 || Integer.parseInt(ddd) > 99) {
            throw new IllegalArgumentException("Telefone invalido.");
        }

        return "+55" + digitos;
    }
}
