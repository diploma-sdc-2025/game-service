package org.java.diploma.service.game.repository;

import org.java.diploma.service.game.entity.Match;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MatchRepositoryIntegrationTest {

    @Autowired
    MatchRepository repo;

    @Test
    void save_andFindById() {
        Match m = new Match();
        m.setStatus("WAITING");

        Match saved = repo.save(m);

        assertThat(repo.findById(saved.getId())).isPresent();
    }
}

