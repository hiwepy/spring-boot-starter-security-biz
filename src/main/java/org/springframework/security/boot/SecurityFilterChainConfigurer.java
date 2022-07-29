/*
 * Copyright (c) 2018, hiwepy (https://github.com/hiwepy).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.springframework.security.boot;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.boot.biz.authentication.*;
import org.springframework.security.boot.biz.authentication.nested.MatchedAuthenticationEntryPoint;
import org.springframework.security.boot.biz.authentication.nested.MatchedAuthenticationFailureHandler;
import org.springframework.security.boot.biz.authentication.nested.MatchedAuthenticationSuccessHandler;
import org.springframework.security.boot.biz.property.*;
import org.springframework.security.boot.biz.property.header.*;
import org.springframework.security.boot.biz.userdetails.UserDetailsServiceAdapter;
import org.springframework.security.boot.utils.StringUtils;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.*;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.ContentSecurityPolicyConfig;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.authentication.logout.*;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.session.InvalidSessionStrategy;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.security.web.session.SimpleRedirectInvalidSessionStrategy;
import org.springframework.security.web.session.SimpleRedirectSessionInformationExpiredStrategy;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Web Security Filter Chain Adapter
 * @author ： <a href="https://github.com/hiwepy">wandl</a>
 */
public abstract class SecurityFilterChainConfigurer {

	private Pattern rolesPattern = Pattern.compile("roles\\[(\\S+)\\]");
	private Pattern permsPattern = Pattern.compile("perms\\[(\\S+)\\]");
	private Pattern ipaddrPattern = Pattern.compile("ipaddr\\[(\\S+)\\]");
	private final SecurityBizProperties bizProperties;
	private final SecurityAuthcProperties authcProperties;
	private final List<AuthenticationProvider> authenticationProviders;


	public SecurityFilterChainConfigurer(SecurityBizProperties bizProperties, SecurityAuthcProperties authcProperties,
										 List<AuthenticationProvider> authenticationProviders) {
		this.bizProperties = bizProperties;
		this.authcProperties = authcProperties;
		this.authenticationProviders = authenticationProviders;
	}

	protected AccessDeniedHandler accessDeniedHandler() {
		AccessDeniedHandler accessDeniedHandler = new AccessDeniedHandlerImpl();
		return accessDeniedHandler;
	}

	protected AuthenticatingFailureCounter authenticatingFailureCounter() {
		AuthenticatingFailureRequestCounter failureCounter = new AuthenticatingFailureRequestCounter();
		failureCounter.setRetryTimesKeyParameter(authcProperties.getRetry().getRetryTimesKeyParameter());
		return failureCounter;
	}

	protected PostRequestAuthenticationEntryPoint authenticationEntryPoint(
			List<MatchedAuthenticationEntryPoint> entryPoints) {
		PostRequestAuthenticationEntryPoint entryPoint = new PostRequestAuthenticationEntryPoint(
				authcProperties.getPathPattern(), entryPoints);
		entryPoint.setForceHttps(authcProperties.getEntryPoint().isForceHttps());
		entryPoint.setStateless(bizProperties.isStateless());
		entryPoint.setUseForward(authcProperties.getEntryPoint().isUseForward());
		return entryPoint;
	}

	protected PostRequestAuthenticationFailureHandler authenticationFailureHandler(
			List<AuthenticationListener> authenticationListeners,
			List<MatchedAuthenticationFailureHandler> failureHandlers) {

		PostRequestAuthenticationFailureHandler failureHandler = new PostRequestAuthenticationFailureHandler(
				authenticationListeners, failureHandlers);

		failureHandler.setAllowSessionCreation(authcProperties.getSessionMgt().isAllowSessionCreation());
		failureHandler.setDefaultFailureUrl(authcProperties.getSessionMgt().getFailureUrl());
		failureHandler.setRedirectStrategy(redirectStrategy());
		failureHandler.setStateless(bizProperties.isStateless());
		failureHandler.setUseForward(authcProperties.getSessionMgt().isUseForward());

		return failureHandler;

	}

	protected ForwardAuthenticationFailureHandler authenticationFailureForwardHandler(String forwardUrl) {
		return new ForwardAuthenticationFailureHandler(forwardUrl);
	}

	protected SimpleUrlAuthenticationFailureHandler authenticationFailureSimpleUrlHandler() {

		SimpleUrlAuthenticationFailureHandler failureHandler = new SimpleUrlAuthenticationFailureHandler();

		failureHandler.setAllowSessionCreation(authcProperties.getSessionMgt().isAllowSessionCreation());
		failureHandler.setDefaultFailureUrl(authcProperties.getSessionMgt().getFailureUrl());
		failureHandler.setRedirectStrategy(redirectStrategy());
		failureHandler.setUseForward(authcProperties.getSessionMgt().isUseForward());

		return failureHandler;
	}

	public AuthenticationManager authenticationManagerBean() throws Exception {
		ProviderManager authenticationManager = new ProviderManager(authenticationProviders);
		// 不擦除认证密码，擦除会导致TokenBasedRememberMeServices因为找不到Credentials再调用UserDetailsService而抛出UsernameNotFoundException
		authenticationManager.setEraseCredentialsAfterAuthentication(false);
		return authenticationManager;
	}

	public PostRequestAuthenticationProvider authenticationProvider(UserDetailsServiceAdapter userDetailsService,
			PasswordEncoder passwordEncoder) {
		return new PostRequestAuthenticationProvider(userDetailsService, passwordEncoder);
	}

	protected PostRequestAuthenticationSuccessHandler authenticationSuccessHandler(
			List<AuthenticationListener> authenticationListeners,
			List<MatchedAuthenticationSuccessHandler> successHandlers) {

		PostRequestAuthenticationSuccessHandler successHandler = new PostRequestAuthenticationSuccessHandler(
				authenticationListeners, successHandlers);
		successHandler.setAlwaysUseDefaultTargetUrl(authcProperties.isAlwaysUseDefaultTargetUrl());
		successHandler.setDefaultTargetUrl(authcProperties.getSuccessUrl());
		successHandler.setRedirectStrategy(redirectStrategy());
		successHandler.setRequestCache(requestCache());
		successHandler.setStateless(bizProperties.isStateless());
		successHandler.setTargetUrlParameter(authcProperties.getTargetUrlParameter());
		successHandler.setUseReferer(authcProperties.isUseReferer());

		return successHandler;
	}

	protected void configure(AuthenticationManagerBuilder auth) throws Exception {
		for (AuthenticationProvider authenticationProvider : authenticationProviders) {
			auth.authenticationProvider(authenticationProvider);
		}
	}

	Customizer<ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry> authorizeRequestsCustomizer(){
		return (requests) -> {

			requests.anyRequest().authenticated();

			// 对过滤链按过滤器名称进行分组
			Map<Object, List<Entry<String, String>>> groupingMap = bizProperties.getFilterChainDefinitionMap().entrySet()
					.stream().collect(Collectors.groupingBy(Entry::getValue, TreeMap::new, Collectors.toList()));

			List<Entry<String, String>> noneEntries = groupingMap.get("anon");
			List<String> permitMatchers = new ArrayList<String>();
			if (!CollectionUtils.isEmpty(noneEntries)) {
				permitMatchers = noneEntries.stream().map(mapper -> {
					return mapper.getKey();
				}).collect(Collectors.toList());
			}
			requests.antMatchers(permitMatchers.toArray(new String[permitMatchers.size()])).anonymous();

			// https://www.jianshu.com/p/01498e0e0c83
			Set<Object> keySet = groupingMap.keySet();
			for (Object key : keySet) {
				// Ant表达式 = roles[xxx]
				Matcher rolesMatcher = rolesPattern.matcher(key.toString());
				if (rolesMatcher.find()) {

					List<String> antPatterns = groupingMap.get(key.toString()).stream().map(mapper -> {
						return mapper.getKey();
					}).collect(Collectors.toList());
					// 角色
					String[] roles = StringUtils.split(rolesMatcher.group(1), ",");
					if (ArrayUtils.isNotEmpty(roles)) {
						if (roles.length > 1) {
							// 如果用户具备给定角色中的某一个的话，就允许访问
							requests.antMatchers(antPatterns.toArray(new String[antPatterns.size()])).hasAnyRole(roles);
						} else {
							// 如果用户具备给定角色的话，就允许访问
							requests.antMatchers(antPatterns.toArray(new String[antPatterns.size()])).hasRole(roles[0]);
						}
					}
				}
				// Ant表达式 = perms[xxx]
				Matcher permsMatcher = permsPattern.matcher(key.toString());
				if (permsMatcher.find()) {

					List<String> antPatterns = groupingMap.get(key.toString()).stream().map(mapper -> {
						return mapper.getKey();
					}).collect(Collectors.toList());
					// 权限标记
					String[] perms = StringUtils.split(permsMatcher.group(1), ",");
					if (ArrayUtils.isNotEmpty(perms)) {
						if (perms.length > 1) {
							// 如果用户具备给定全权限的某一个的话，就允许访问
							requests.antMatchers(antPatterns.toArray(new String[antPatterns.size()])).hasAnyAuthority(perms);
						} else {
							// 如果用户具备给定权限的话，就允许访问
							requests.antMatchers(antPatterns.toArray(new String[antPatterns.size()])).hasAuthority(perms[0]);
						}
					}
				}
				// Ant表达式 = ipaddr[192.168.1.0/24]
				Matcher ipMatcher = ipaddrPattern.matcher(key.toString());
				if (rolesMatcher.find()) {

					List<String> antPatterns = groupingMap.get(key.toString()).stream().map(mapper -> {
						return mapper.getKey();
					}).collect(Collectors.toList());
					// ipaddress
					String ipaddr = ipMatcher.group(1);
					if (StringUtils.hasText(ipaddr)) {
						// 如果请求来自给定IP地址的话，就允许访问
						requests.antMatchers(antPatterns.toArray(new String[antPatterns.size()])).hasIpAddress(ipaddr);
					}
				}
			}

		};
	}

	/**
	 * Headers 配置
	 *
	 * @author ： <a href="https://github.com/hiwepy">wandl</a>
	 * @param http  the HttpSecurity
	 * @param properties the Security Headers Properties
	 * @throws Exception the Exception
	 */

	Customizer<HeadersConfigurer<HttpSecurity>> headersCustomizer(SecurityHeadersProperties properties){
		return (configurer) -> {
			if (properties.isEnabled()) {

				HeaderContentTypeOptionsProperties contentTypeOptions = properties.getContentTypeOptions();
				if (contentTypeOptions.isEnabled()) {
					configurer.contentTypeOptions();
				} else {
					configurer.contentTypeOptions().disable();
				}

				HeaderXssProtectionProperties xssProtection = properties.getXssProtection();
				if (xssProtection.isEnabled()) {
					configurer.xssProtection().xssProtectionEnabled(xssProtection.isEnabled()).block(xssProtection.isBlock());
				} else {
					configurer.xssProtection().disable();
				}

				HeaderCacheControlProperties cacheControl = properties.getCacheControl();
				if (cacheControl.isEnabled()) {
					configurer.cacheControl();
				} else {
					configurer.cacheControl().disable();
				}

				HeaderHstsProperties hsts = properties.getHsts();
				if (hsts.isEnabled()) {
					configurer.httpStrictTransportSecurity()
							.includeSubDomains(hsts.isIncludeSubDomains())
							.maxAgeInSeconds(hsts.getMaxAgeInSeconds());
				} else {
					configurer.httpStrictTransportSecurity().disable();
				}

				HeaderFrameOptionsProperties frameOptions = properties.getFrameOptions();
				if (frameOptions.isEnabled()) {
					FrameOptionsConfig config = configurer.frameOptions();
					if (frameOptions.isDeny()) {
						config.deny();
					} else if (frameOptions.isSameOrigin()) {
						config.sameOrigin();
					}
				} else {
					configurer.frameOptions().disable();
				}

				HeaderHpkpProperties hpkp = properties.getHpkp();
				if (hpkp.isEnabled()) {
					configurer.httpPublicKeyPinning()
							.includeSubDomains(hpkp.isIncludeSubDomains())
							.maxAgeInSeconds(hpkp.getMaxAgeInSeconds())
							.reportOnly(hpkp.isReportOnly())
							.reportUri(hpkp.getReportUri())
							.withPins(hpkp.getPins())
							.addSha256Pins(hpkp.getSha256Pins());
				} else {
					configurer.httpPublicKeyPinning().disable();
				}

				HeaderContentSecurityPolicyProperties contentSecurityPolicy = properties.getContentSecurityPolicy();
				if (contentSecurityPolicy.isEnabled()) {
					ContentSecurityPolicyConfig config = configurer.contentSecurityPolicy(contentSecurityPolicy.getPolicyDirectives());
					if (contentSecurityPolicy.isReportOnly()) {
						config.reportOnly();
					}
				}

				HeaderReferrerPolicyProperties referrerPolicy = properties.getReferrerPolicy();
				if (referrerPolicy.isEnabled()) {
					configurer.referrerPolicy();
				}

				HeaderFeaturePolicyProperties featurePolicy = properties.getFeaturePolicy();
				if (featurePolicy.isEnabled()) {
					configurer.featurePolicy(featurePolicy.getPolicyDirectives());
				}
			}
		};
	}

	Customizer<SessionManagementConfigurer<HttpSecurity>> sessionManagementCustomizer(SecuritySessionMgtProperties sessionMgt,
																					  SecurityLogoutProperties logout,
																					  InvalidSessionStrategy invalidSessionStrategy,
																					  SessionRegistry sessionRegistry,
																					  SessionInformationExpiredStrategy sessionInformationExpiredStrategy,
																					  AuthenticationFailureHandler sessionAuthenticationFailureHandler,
																					  SessionAuthenticationStrategy sessionAuthenticationStrategy){
		return (configurer) -> {
			configurer.enableSessionUrlRewriting(sessionMgt.isEnableSessionUrlRewriting())
					.invalidSessionStrategy(invalidSessionStrategy)
					.invalidSessionUrl(logout.getLogoutUrl())
					.maximumSessions(sessionMgt.getMaximumSessions())
					.maxSessionsPreventsLogin(sessionMgt.isMaxSessionsPreventsLogin())
					.expiredSessionStrategy(sessionInformationExpiredStrategy)
					.expiredUrl(logout.getLogoutUrl())
					.sessionRegistry(sessionRegistry)
					.and()
					.sessionAuthenticationErrorUrl(sessionMgt.getFailureUrl())
					.sessionAuthenticationFailureHandler(sessionAuthenticationFailureHandler)
					.sessionAuthenticationStrategy(sessionAuthenticationStrategy)
					.sessionCreationPolicy(sessionMgt.getCreationPolicy());
		};
	}

	Customizer<CsrfConfigurer<HttpSecurity>> csrfCustomizer(SecurityHeaderCsrfProperties csrf){
		return (configurer) -> {
			// CSRF 配置
			if (csrf.isEnabled()) {
				configurer.csrfTokenRepository(this.csrfTokenRepository())
						.ignoringAntMatchers(StringUtils.tokenizeToStringArray(csrf.getIgnoringAntMatchers()))
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());
			} else {
				configurer.disable();
			}
		};
	}

	Customizer<CorsConfigurer<HttpSecurity>> corsCustomizer(SecurityHeaderCorsProperties cors){
		return (configurer) -> {
			if (cors.isEnabled()) {
				configurer.configurationSource(this.configurationSource(cors));
			} else {
				configurer.disable();
			}
		};
	}

	Customizer<LogoutConfigurer<HttpSecurity>> logoutCustomizer(SecurityLogoutProperties logout, LogoutHandler logoutHandler, LogoutSuccessHandler logoutSuccessHandler){
		return (configurer) -> {
			configurer.logoutUrl(logout.getPathPatterns())
					.logoutSuccessUrl(logout.getLogoutSuccessUrl())
					.addLogoutHandler(logoutHandler)
					.clearAuthentication(logout.isClearAuthentication())
					.invalidateHttpSession(logout.isInvalidateHttpSession());
			if(Objects.nonNull(logoutSuccessHandler)){
				configurer.logoutSuccessHandler(logoutSuccessHandler);
			}
		};
	}

	protected CorsConfigurationSource configurationSource(SecurityHeaderCorsProperties cors) {

		UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();

		/**
		 * 批量设置参数
		 */
		PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();

		map.from(cors.isAllowInitLookupPath()).to(configurationSource::setAllowInitLookupPath);
		map.from(cors.getCorsConfigurations()).to(configurationSource::setCorsConfigurations);
		configurationSource.setPathMatcher(null);

		return configurationSource;
	}

	protected CsrfTokenRepository csrfTokenRepository() {
		// Session 管理器配置参数
		if (SessionFixationPolicy.CHANGE_SESSION_ID.equals(authcProperties.getSessionMgt().getFixationPolicy())) {
			return new CookieCsrfTokenRepository();
		}
		return new HttpSessionCsrfTokenRepository();
	}

	protected InvalidSessionStrategy invalidSessionStrategy() {
		SimpleRedirectInvalidSessionStrategy invalidSessionStrategy = new SimpleRedirectInvalidSessionStrategy(
				authcProperties.getSessionMgt().getFailureUrl());
		invalidSessionStrategy.setCreateNewSession(authcProperties.getSessionMgt().isAllowSessionCreation());
		return invalidSessionStrategy;
	}

	protected LogoutHandler logoutHandler(List<LogoutHandler> logoutHandlers) {
		return new CompositeLogoutHandler(logoutHandlers);
	}


	protected LogoutSuccessHandler logoutSuccessForwardHandler(String targetUrl) {
		return new ForwardLogoutSuccessHandler(targetUrl);
	}

	protected LogoutSuccessHandler lLogoutSuccessSimpleUrlHandler() {
		return new SimpleUrlLogoutSuccessHandler();
	}

	protected RedirectStrategy redirectStrategy() {
		DefaultRedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
		redirectStrategy.setContextRelative(authcProperties.getRedirect().isContextRelative());
		return redirectStrategy;
	}


	protected RequestCache requestCache() {
		HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		requestCache.setCreateSessionAllowed(authcProperties.getSessionMgt().isAllowSessionCreation());
		requestCache.setSessionAttrName(authcProperties.getSessionMgt().getSessionAttrName());
		return requestCache;
	}

	protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		// Session 管理器配置参数
		SecuritySessionMgtProperties sessionMgt = authcProperties.getSessionMgt();
		if (SessionFixationPolicy.CHANGE_SESSION_ID.equals(sessionMgt.getFixationPolicy())) {
			return new ChangeSessionIdAuthenticationStrategy();
		} else if (SessionFixationPolicy.MIGRATE_SESSION.equals(sessionMgt.getFixationPolicy())) {
			return new SessionFixationProtectionStrategy();
		} else if (SessionFixationPolicy.NEW_SESSION.equals(sessionMgt.getFixationPolicy())) {
			SessionFixationProtectionStrategy sessionFixationProtectionStrategy = new SessionFixationProtectionStrategy();
			sessionFixationProtectionStrategy.setMigrateSessionAttributes(false);
			return sessionFixationProtectionStrategy;
		} else {
			return new NullAuthenticatedSessionStrategy();
		}
	}

	protected SessionInformationExpiredStrategy sessionInformationExpiredStrategy() {
		return new SimpleRedirectSessionInformationExpiredStrategy(authcProperties.getSessionMgt().getFailureUrl(),
				redirectStrategy());
	}

	protected SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}


}
