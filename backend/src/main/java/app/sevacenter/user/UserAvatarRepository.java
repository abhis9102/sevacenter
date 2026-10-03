package app.sevacenter.user;

import org.springframework.data.jpa.repository.JpaRepository;

/** RLS scopes it to the current tenant, like every app_user query. */
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
}
