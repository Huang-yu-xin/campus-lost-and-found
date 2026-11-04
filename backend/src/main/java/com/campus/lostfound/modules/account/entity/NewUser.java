package com.campus.lostfound.modules.account.entity;

/** 新建 users 记录的参数；插入后由数据库回填 id。 */
public class NewUser {

    private Long id;
    private Long studentRecordId;
    private String role;
    private String nickname;
    private String email;
    private String phone;
    private String passwordHash;

    public static NewUser student(long studentRecordId, String nickname, String email, String passwordHash) {
        NewUser user = new NewUser();
        user.studentRecordId = studentRecordId;
        user.role = "STUDENT";
        user.nickname = nickname;
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }

    public static NewUser admin(String nickname, String email, String passwordHash) {
        NewUser user = new NewUser();
        user.role = "ADMIN";
        user.nickname = nickname;
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentRecordId() {
        return studentRecordId;
    }

    public void setStudentRecordId(Long studentRecordId) {
        this.studentRecordId = studentRecordId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
