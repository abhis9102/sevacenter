package app.sevacenter.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A staff member's profile image: the avatar columns of {@code app_user}, mapped on their own so
 * the bytes (up to 2 MB) load only when the image itself is requested, never with the user (which
 * StaffSessionFilter re-reads on every request). Same table, so RLS applies unchanged.
 */
@Entity
@Table(name = "app_user")
public class UserAvatar {

    @Id
    private Long id;

    @Column(name = "avatar_data")
    private byte[] data;

    @Column(name = "avatar_content_type")
    private String contentType;

    protected UserAvatar() { }

    void replace(byte[] data, String contentType) {
        this.data = data;
        this.contentType = contentType;
    }

    void clear() {
        this.data = null;
        this.contentType = null;
    }

    public byte[] getData() { return data; }
    public String getContentType() { return contentType; }
}
