package db.migration;

import java.sql.PreparedStatement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Cria o usuario administrador inicial a partir das variaveis de ambiente
 * ADMIN_EMAIL e ADMIN_PASSWORD (lidas diretamente do ambiente, nao do
 * Spring Environment, porque migrations Flyway rodam fora do contexto do
 * Spring). Se as variaveis nao estiverem definidas, a migration e' um
 * no-op registrado — util para o perfil de teste, onde os proprios testes
 * criam os usuarios de que precisam.
 */
public class V5__CriaUsuarioAdministradorInicial extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V5__CriaUsuarioAdministradorInicial.class);

    @Override
    public void migrate(Context context) throws Exception {
        String email = System.getenv("ADMIN_EMAIL");
        String senha = System.getenv("ADMIN_PASSWORD");

        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            log.warn("ADMIN_EMAIL/ADMIN_PASSWORD nao definidos - nenhum usuario administrador inicial foi criado.");
            return;
        }

        String senhaHash = new BCryptPasswordEncoder().encode(senha);

        String sql = """
                INSERT INTO usuario (nome, email, senha_hash, perfil, ativo)
                VALUES (?, ?, ?, 'ADMIN', TRUE)
                ON CONFLICT (email) DO NOTHING
                """;

        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            statement.setString(1, "Administrador");
            statement.setString(2, email.trim().toLowerCase());
            statement.setString(3, senhaHash);
            statement.executeUpdate();
        }
    }
}
