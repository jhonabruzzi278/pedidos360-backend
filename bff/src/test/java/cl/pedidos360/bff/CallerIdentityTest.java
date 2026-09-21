package cl.pedidos360.bff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class CallerIdentityTest {
  private static Jwt jwt(Map<String, Object> claims) {
    return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
  }

  @Test
  void theOidOfEntraIdIsTheStableId() {
    CallerIdentity caller = CallerIdentity.from(jwt(Map.of("oid", "0000-oid", "sub", "pairwise-sub", "name", "Ana Pérez")));

    assertThat(caller.id()).isEqualTo("0000-oid");
    assertThat(caller.name()).isEqualTo("Ana Pérez");
  }

  @Test
  void withoutOidTheSubjectIsUsed() {
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "local-admin"))).id()).isEqualTo("local-admin");
  }

  @Test
  void aTokenThatDoesNotIdentifyTheUserIsRejected() {
    assertThatThrownBy(() -> CallerIdentity.from(jwt(Map.of("name", "Sin id"))))
        .isInstanceOf(InvalidBearerTokenException.class);
    assertThatThrownBy(() -> CallerIdentity.from(jwt(Map.of("oid", "   ", "sub", ""))))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  @Test
  void aClaimThatIsNotTextIsIgnored() {
    CallerIdentity caller = CallerIdentity.from(jwt(Map.of("oid", 12345, "sub", "abc", "name", 99)));

    assertThat(caller.id()).isEqualTo("abc");
    assertThat(caller.name()).isEqualTo("abc");
  }

  @Test
  void theNameFallsBackToTheUsernameThenTheEmailThenTheId() {
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "u1", "preferred_username", "ana@taller.cl"))).name())
        .isEqualTo("ana@taller.cl");
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "u1", "email", "ana@correo.cl"))).name())
        .isEqualTo("ana@correo.cl");
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "u1"))).name()).isEqualTo("u1");
  }

  @Test
  void theEmailComesFromTheEmailClaimOrFromAUsernameThatLooksLikeOne() {
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "u1", "email", "a@b.cl", "preferred_username", "otro@x.cl"))).email())
        .isEqualTo("a@b.cl");
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "u1", "preferred_username", "ana@taller.cl"))).email())
        .isEqualTo("ana@taller.cl");
    assertThat(CallerIdentity.from(jwt(Map.of("sub", "u1", "preferred_username", "ana"))).email()).isEmpty();
  }

  @Test
  void controlCharactersAreRemovedAndTheTextsAreBoundedToTheirColumns() {
    CallerIdentity caller = CallerIdentity.from(jwt(Map.of(
        "sub", "s".repeat(80), "name", "  Ana\nPérez\t" + "x".repeat(200), "email", "e".repeat(200))));

    assertThat(caller.id()).hasSize(CallerIdentity.MAX_ID);
    assertThat(caller.name()).hasSize(CallerIdentity.MAX_NAME).startsWith("AnaPérezx");
    assertThat(caller.email()).hasSize(CallerIdentity.MAX_EMAIL);
  }
}
