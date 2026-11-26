package cn.edu.scuec.icpc.lab.auth;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    java.util.Optional<RefreshToken> findByTokenHash(String tokenHash);

    java.util.List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);
}