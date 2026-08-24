package com.barbearia.fiscal.gateway;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Implementacao atual do {@link FiscalGateway}: gera um recibo interno em
 * PDF (OpenPDF), sem integracao com nenhuma prefeitura ou provedor fiscal —
 * isso e escopo da Fase 16. Nao depende de browser headless, so da
 * biblioteca de geracao de PDF.
 */
@Component
public class ReciboFiscalGateway implements FiscalGateway {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));
    private static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");

    private static final Font FONTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FONTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC);
    private static final Font FONTE_SECAO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font FONTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font FONTE_TABELA_CABECALHO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font FONTE_TABELA_CELULA = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FONTE_TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font FONTE_RODAPE = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC);

    @Override
    public DocumentoFiscal emitirNotaFiscal(DadosComprovante dados) {
        try {
            Document documento = new Document(PageSize.A5);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, saida);
            documento.open();

            adicionarCabecalho(documento, dados);
            adicionarDadosCliente(documento, dados);
            adicionarTabelaItens(documento, dados);
            adicionarTotais(documento, dados);
            adicionarRodape(documento);

            documento.close();
            return new DocumentoFiscal(saida.toByteArray(), null);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF do comprovante numero " + dados.numero(), e);
        }
    }

    @Override
    public DocumentoFiscal consultarNotaFiscal(String identificadorExterno) {
        throw new UnsupportedOperationException(
                "Recibo interno nao possui consulta externa — disponivel apenas a partir da Fase 16 (NFS-e real).");
    }

    @Override
    public void cancelarNotaFiscal(String identificadorExterno, String motivo) {
        throw new UnsupportedOperationException(
                "Recibo interno nao possui cancelamento externo — disponivel apenas a partir da Fase 16 (NFS-e real).");
    }

    private void adicionarCabecalho(Document documento, DadosComprovante dados) throws Exception {
        Paragraph titulo = new Paragraph(dados.barbeariaNome(), FONTE_TITULO);
        titulo.setAlignment(Element.ALIGN_CENTER);
        documento.add(titulo);

        if (dados.barbeariaCnpj() != null || dados.barbeariaEndereco() != null) {
            StringBuilder linha = new StringBuilder();
            if (dados.barbeariaCnpj() != null) {
                linha.append("CNPJ: ").append(dados.barbeariaCnpj());
            }
            if (dados.barbeariaEndereco() != null) {
                if (!linha.isEmpty()) {
                    linha.append(" — ");
                }
                linha.append(dados.barbeariaEndereco());
            }
            Paragraph infoBarbearia = new Paragraph(linha.toString(), FONTE_SUBTITULO);
            infoBarbearia.setAlignment(Element.ALIGN_CENTER);
            documento.add(infoBarbearia);
        }

        documento.add(new Paragraph(" "));
        Paragraph tituloComprovante = new Paragraph(
                "COMPROVANTE DE ATENDIMENTO Nº " + String.format("%06d", dados.numero()), FONTE_SECAO);
        tituloComprovante.setAlignment(Element.ALIGN_CENTER);
        documento.add(tituloComprovante);

        Paragraph aviso = new Paragraph(
                "Este documento nao possui valor fiscal. E apenas um comprovante interno de atendimento.",
                FONTE_SUBTITULO);
        aviso.setAlignment(Element.ALIGN_CENTER);
        documento.add(aviso);
        documento.add(new Paragraph(" "));
    }

    private void adicionarDadosCliente(Document documento, DadosComprovante dados) throws Exception {
        documento.add(new Paragraph("Data/hora: "
                + DATA_HORA.format(dados.emitidoEm().atZone(FUSO_PADRAO)), FONTE_TEXTO));
        documento.add(new Paragraph("Cliente: " + dados.clienteNome()
                + (dados.clienteTelefone() != null ? " — " + dados.clienteTelefone() : ""), FONTE_TEXTO));
        if (dados.profissionalNome() != null) {
            documento.add(new Paragraph("Profissional: " + dados.profissionalNome(), FONTE_TEXTO));
        }
        documento.add(new Paragraph(" "));
    }

    private void adicionarTabelaItens(Document documento, DadosComprovante dados) throws Exception {
        PdfPTable tabela = new PdfPTable(new float[] { 4f, 1f, 1.3f, 1.3f });
        tabela.setWidthPercentage(100);

        for (String cabecalho : new String[] { "Item", "Qtd.", "Unit.", "Total" }) {
            PdfPCell celula = new PdfPCell(new Phrase(cabecalho, FONTE_TABELA_CABECALHO));
            celula.setPadding(4);
            tabela.addCell(celula);
        }

        for (DadosComprovanteItem item : dados.itens()) {
            tabela.addCell(celulaTexto(item.descricao()));
            tabela.addCell(celulaTexto(String.valueOf(item.quantidade())));
            tabela.addCell(celulaValor(item.valorUnitario()));
            tabela.addCell(celulaValor(item.valorLiquido()));
        }

        documento.add(tabela);
        documento.add(new Paragraph(" "));
    }

    private PdfPCell celulaTexto(String texto) {
        PdfPCell celula = new PdfPCell(new Phrase(texto, FONTE_TABELA_CELULA));
        celula.setPadding(4);
        return celula;
    }

    private PdfPCell celulaValor(BigDecimal valor) {
        PdfPCell celula = new PdfPCell(new Phrase(formatarMoeda(valor), FONTE_TABELA_CELULA));
        celula.setPadding(4);
        celula.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return celula;
    }

    private void adicionarTotais(Document documento, DadosComprovante dados) throws Exception {
        documento.add(linhaAlinhadaDireita("Subtotal: " + formatarMoeda(dados.subtotal()), FONTE_TEXTO));
        if (dados.descontoValor() != null && dados.descontoValor().signum() > 0) {
            documento.add(linhaAlinhadaDireita("Desconto: -" + formatarMoeda(dados.descontoValor()), FONTE_TEXTO));
        }
        documento.add(linhaAlinhadaDireita("Total: " + formatarMoeda(dados.valorTotal()), FONTE_TOTAL));
        documento.add(linhaAlinhadaDireita("Forma de pagamento: " + dados.formaPagamento(), FONTE_TEXTO));
        documento.add(new Paragraph(" "));
    }

    private Paragraph linhaAlinhadaDireita(String texto, Font fonte) {
        Paragraph paragrafo = new Paragraph(texto, fonte);
        paragrafo.setAlignment(Element.ALIGN_RIGHT);
        return paragrafo;
    }

    private void adicionarRodape(Document documento) throws Exception {
        Paragraph rodape = new Paragraph(
                "Comprovante gerado automaticamente pelo Sistema para Barbearia.", FONTE_RODAPE);
        rodape.setAlignment(Element.ALIGN_CENTER);
        documento.add(rodape);
    }

    private String formatarMoeda(BigDecimal valor) {
        return "R$ " + valor.setScale(2, java.math.RoundingMode.HALF_UP).toString().replace('.', ',');
    }
}
