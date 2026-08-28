package com.barbearia.ia.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.barbearia.ia.domain.ConfiguracaoIa;
import com.barbearia.ia.dto.AtualizarConfiguracaoIaRequest;
import com.barbearia.ia.dto.ConfiguracaoIaDto;
import com.barbearia.ia.repository.ConfiguracaoIaRepository;
import com.barbearia.mensageria.repository.ConversaRepository;
import com.barbearia.shared.exception.RecursoNaoEncontradoException;

@Service
@RequiredArgsConstructor
public class ConfiguracaoIaService {

    private final ConfiguracaoIaRepository configuracaoIaRepository;
    private final ConversaRepository conversaRepository;

    @Transactional(readOnly = true)
    public ConfiguracaoIaDto obter() {
        return paraDto(buscarSingleton());
    }

    /**
     * Kill switch obrigatorio do PRD (Fase 10): desligar aqui joga toda conversa em modo IA
     * imediatamente para HUMANO, nao so impede novas respostas automaticas.
     */
    @Transactional
    public ConfiguracaoIaDto atualizar(AtualizarConfiguracaoIaRequest requisicao) {
        ConfiguracaoIa configuracao = buscarSingleton();
        boolean estavaAtivo = configuracao.isAtivo();

        configuracao.setAtivo(requisicao.ativo());
        configuracao.setLimiteTurnos(requisicao.limiteTurnos());
        configuracao.setTetoCustoMensalCentavos(requisicao.tetoCustoMensalCentavos());
        configuracao = configuracaoIaRepository.save(configuracao);

        if (estavaAtivo && !requisicao.ativo()) {
            conversaRepository.escalarTodasParaHumano("Kill switch da IA foi desligado.");
        }

        return paraDto(configuracao);
    }

    private ConfiguracaoIa buscarSingleton() {
        return configuracaoIaRepository.findById(ConfiguracaoIa.ID_SINGLETON)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Configuracao do agente de IA nao encontrada. Verifique se as migrations foram executadas."));
    }

    private ConfiguracaoIaDto paraDto(ConfiguracaoIa configuracao) {
        return new ConfiguracaoIaDto(configuracao.isAtivo(), configuracao.getLimiteTurnos(),
                configuracao.getTetoCustoMensalCentavos());
    }
}
