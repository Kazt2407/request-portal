package dev.requestportal.org.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    /** Race-free first sign-in: concurrent requests for a new user cannot both insert. */
    @Modifying
    @Query(value = """
            insert into account (id, email, full_name)
            values (:id, :email, :fullName)
            on conflict (id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(UUID id, String email, String fullName);
}
