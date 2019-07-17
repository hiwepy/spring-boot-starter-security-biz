package org.springframework.security.boot.biz.userdetails;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * @author <a href="https://github.com/vindell">vindell</a>
 */
@SuppressWarnings("serial")
public class SecurityPrincipal extends User implements Cloneable {
	
	/**
	 * 用户ID（用户来源表Id）
	 */
	private String userid;
	/**
	 * 用户Key：用户业务表中的唯一ID
	 */
	private String userkey;
	/**
	 * 用户Code：用户业务表中的唯一编码
	 */
	private String usercode;
	/**
	 * 用户密码盐：用于密码加解密
	 */
	private String salt;
	/**
	 * 用户秘钥：用于用户JWT加解密
	 */
	private String secret;
	/**
	 * 用户别名（昵称）
	 */
	private String alias;
	/**
	 * 用户角色ID
	 */
	private String roleid;
	/**
	 * 用户角色Key
	 */
	private String role;
	/**
	 * 账号首次登陆标记
	 */
	private boolean initial = false;
	/**
	 * 用户拥有角色列表
	 */
	private Set<String> roles;
	/**
	 * 用户权限标记列表
	 */
	private Set<String> perms;
	/**
	 * 用户数据
	 */
	private Map<String, Object> profile = new HashMap<String, Object>();
	
	public SecurityPrincipal(String username, String password, String... roles) {
		super(username, password, roleAuthorities(Arrays.asList(roles)));
	}
	
	public static Collection<? extends GrantedAuthority> roleAuthorities(List<String> roles){
		
		if (roles == null) { 
			throw new InsufficientAuthenticationException("User has no roles assigned");
		}
        List<GrantedAuthority> authorities = roles.stream()
                .map(authority -> new SimpleGrantedAuthority(authority))
                .collect(Collectors.toList());
        
		return authorities;
	}
	
	public SecurityPrincipal(String username, String password, Collection<? extends GrantedAuthority> authorities) {
		super(username, password, authorities);
	}

	public SecurityPrincipal(String username, String password, boolean enabled, boolean accountNonExpired,
			boolean credentialsNonExpired, boolean accountNonLocked,
			Collection<? extends GrantedAuthority> authorities) {
		super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
	}
	
	public boolean isInitial() {
		return initial;
	}

	public void setInitial(boolean initial) {
		this.initial = initial;
	}

	public String getUserid() {
		return userid;
	}

	public void setUserid(String userid) {
		this.userid = userid;
	}

	public String getUserkey() {
		return userkey;
	}

	public void setUserkey(String userkey) {
		this.userkey = userkey;
	}

	public String getUsercode() {
		return usercode;
	}

	public void setUsercode(String usercode) {
		this.usercode = usercode;
	}

	public String getSalt() {
		return salt;
	}

	public void setSalt(String salt) {
		this.salt = salt;
	}

	public String getCredentialsSalt() {
		return salt;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}
	
	public String getRoleid() {
		return roleid;
	}

	public void setRoleid(String roleid) {
		this.roleid = roleid;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public Set<String> getRoles() {
		return roles;
	}

	public void setRoles(Set<String> roles) {
		this.roles = roles;
	}

	public Set<String> getPerms() {
		return perms;
	}

	public void setPerms(Set<String> perms) {
		this.perms = perms;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public Map<String, Object> getProfile() {
		return profile;
	}

	public void setProfile(Map<String, Object> profile) {
		this.profile = profile;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		SecurityPrincipal user = (SecurityPrincipal) o;
		if (userid != null ? !userid.equals(user.getUserid()) : user.getUserid() != null) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		return userid != null ? userid.hashCode() : 0;
	}

	@Override
	public String toString() {
		return " User {" + "userid=" + userid + ", username='" + getUsername() + '\'' + ", password='" + getPassword() + '\''
				+ ", salt='" + salt + '\'' + ", enabled='" + isEnabled() + '\'' + ", accountNonExpired=" + isAccountNonExpired()
				+ ", credentialsNonExpired=" + isCredentialsNonExpired() + ", accountNonLocked=" + isAccountNonLocked() + '}';
	}

}
