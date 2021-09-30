package org.apereo.cas.config;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.audit.AuditActionResolvers;
import org.apereo.cas.audit.AuditResourceResolvers;
import org.apereo.cas.audit.AuditTrailRecordResolutionPlanConfigurer;
import org.apereo.cas.audit.DelegatedAuthenticationAuditResourceResolver;
import org.apereo.cas.authentication.AuthenticationEventExecutionPlanConfigurer;
import org.apereo.cas.authentication.AuthenticationHandler;
import org.apereo.cas.authentication.AuthenticationMetaDataPopulator;
import org.apereo.cas.authentication.CasSSLContext;
import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.PrincipalFactoryUtils;
import org.apereo.cas.authentication.principal.PrincipalResolver;
import org.apereo.cas.authentication.principal.provision.ChainingDelegatedClientUserProfileProvisioner;
import org.apereo.cas.authentication.principal.provision.DelegatedClientUserProfileProvisioner;
import org.apereo.cas.authentication.principal.provision.GroovyDelegatedClientUserProfileProvisioner;
import org.apereo.cas.authentication.principal.provision.RestfulDelegatedClientUserProfileProvisioner;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.logout.LogoutExecutionPlanConfigurer;
import org.apereo.cas.pac4j.DistributedJEESessionStore;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.support.pac4j.RefreshableDelegatedClients;
import org.apereo.cas.support.pac4j.authentication.ClientAuthenticationMetaDataPopulator;
import org.apereo.cas.support.pac4j.authentication.DefaultDelegatedClientFactory;
import org.apereo.cas.support.pac4j.authentication.DelegatedClientFactory;
import org.apereo.cas.support.pac4j.authentication.DelegatedClientFactoryCustomizer;
import org.apereo.cas.support.pac4j.authentication.RestfulDelegatedClientFactory;
import org.apereo.cas.support.pac4j.authentication.handler.support.DelegatedClientAuthenticationHandler;
import org.apereo.cas.ticket.TicketFactory;
import org.apereo.cas.util.HttpRequestUtils;
import org.apereo.cas.web.cookie.CasCookieBuilder;
import org.apereo.cas.web.support.CookieUtils;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apereo.inspektr.audit.spi.AuditActionResolver;
import org.apereo.inspektr.audit.spi.AuditResourceResolver;
import org.pac4j.core.client.Clients;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.context.session.SessionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This is {@link Pac4jAuthenticationEventExecutionPlanConfiguration}.
 *
 * @author Misagh Moayyed
 * @author Dmitriy Kopylenko
 * @since 5.1.0
 */
@EnableConfigurationProperties(CasConfigurationProperties.class)
@Slf4j
@Configuration(value = "pac4jAuthenticationEventExecutionPlanConfiguration", proxyBeanMethods = false)
public class Pac4jAuthenticationEventExecutionPlanConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "pac4jDelegatedClientFactory")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Autowired
    public DelegatedClientFactory pac4jDelegatedClientFactory(
        final CasConfigurationProperties casProperties,
        final ConfigurableApplicationContext applicationContext,
        final ObjectProvider<List<DelegatedClientFactoryCustomizer>> customizerList,
        @Qualifier("casSslContext")
        final CasSSLContext casSslContext) {
        val rest = casProperties.getAuthn().getPac4j().getRest();
        if (StringUtils.isNotBlank(rest.getUrl())) {
            return new RestfulDelegatedClientFactory(casProperties);
        }
        val customizers = Optional.ofNullable(customizerList.getIfAvailable())
            .map(result -> {
                AnnotationAwareOrderComparator.sortIfNecessary(result);
                return result;
            }).orElse(new ArrayList<>(0));
        return new DefaultDelegatedClientFactory(casProperties, customizers, casSslContext, applicationContext);
    }

    @ConditionalOnMissingBean(name = "delegatedClientDistributedSessionStore")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Autowired
    public SessionStore delegatedClientDistributedSessionStore(final CasConfigurationProperties casProperties,
                                                               @Qualifier("delegatedClientDistributedSessionCookieGenerator")
                                                               final CasCookieBuilder delegatedClientDistributedSessionCookieGenerator,
                                                               @Qualifier("defaultTicketFactory")
                                                               final TicketFactory ticketFactory,
                                                               @Qualifier(CentralAuthenticationService.BEAN_NAME)
                                                               final CentralAuthenticationService centralAuthenticationService) {
        val replicate = casProperties.getAuthn().getPac4j().getCore().isReplicateSessions();
        if (replicate) {
            return new DistributedJEESessionStore(centralAuthenticationService,
                ticketFactory, delegatedClientDistributedSessionCookieGenerator);
        }
        return JEESessionStore.INSTANCE;
    }

    @ConditionalOnMissingBean(name = "delegatedClientDistributedSessionCookieGenerator")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Autowired
    public CasCookieBuilder delegatedClientDistributedSessionCookieGenerator(final CasConfigurationProperties casProperties) {
        val cookie = casProperties.getSessionReplication().getCookie();
        return CookieUtils.buildCookieRetrievingGenerator(cookie);
    }

    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Bean
    @ConditionalOnMissingBean(name = "builtClients")
    @Autowired
    public Clients builtClients(final CasConfigurationProperties casProperties,
                                @Qualifier("pac4jDelegatedClientFactory")
                                final DelegatedClientFactory pac4jDelegatedClientFactory) {
        return new RefreshableDelegatedClients(casProperties.getServer().getLoginUrl(), pac4jDelegatedClientFactory);
    }

    @ConditionalOnMissingBean(name = "clientPrincipalFactory")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public PrincipalFactory clientPrincipalFactory() {
        return PrincipalFactoryUtils.newPrincipalFactory();
    }

    @ConditionalOnMissingBean(name = "clientAuthenticationMetaDataPopulator")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public AuthenticationMetaDataPopulator clientAuthenticationMetaDataPopulator() {
        return new ClientAuthenticationMetaDataPopulator();
    }

    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Bean
    @ConditionalOnMissingBean(name = "clientAuthenticationHandler")
    @Autowired
    public AuthenticationHandler clientAuthenticationHandler(
        final CasConfigurationProperties casProperties,
        @Qualifier("clientPrincipalFactory")
        final PrincipalFactory clientPrincipalFactory,
        @Qualifier("builtClients")
        final Clients builtClients,
        @Qualifier("clientUserProfileProvisioner")
        final DelegatedClientUserProfileProvisioner clientUserProfileProvisioner,
        @Qualifier("delegatedClientDistributedSessionStore")
        final SessionStore delegatedClientDistributedSessionStore,
        @Qualifier(ServicesManager.BEAN_NAME)
        final ServicesManager servicesManager) {
        val pac4j = casProperties.getAuthn().getPac4j().getCore();
        val h = new DelegatedClientAuthenticationHandler(pac4j.getName(), pac4j.getOrder(),
            servicesManager, clientPrincipalFactory, builtClients, clientUserProfileProvisioner,
            delegatedClientDistributedSessionStore);
        h.setTypedIdUsed(pac4j.isTypedIdUsed());
        h.setPrincipalAttributeId(pac4j.getPrincipalAttributeId());
        return h;
    }

    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Bean
    @ConditionalOnMissingBean(name = "clientUserProfileProvisioner")
    @Autowired
    public DelegatedClientUserProfileProvisioner clientUserProfileProvisioner(final CasConfigurationProperties casProperties) {
        val provisioning = casProperties.getAuthn().getPac4j().getProvisioning();
        val chain = new ChainingDelegatedClientUserProfileProvisioner();
        val script = provisioning.getGroovy().getLocation();
        if (script != null) {
            chain.addProvisioner(new GroovyDelegatedClientUserProfileProvisioner(script));
        }
        if (StringUtils.isNotBlank(provisioning.getRest().getUrl())) {
            chain.addProvisioner(new RestfulDelegatedClientUserProfileProvisioner(provisioning.getRest()));
        }
        if (chain.isEmpty()) {
            return DelegatedClientUserProfileProvisioner.noOp();
        }
        return chain;
    }

    @ConditionalOnMissingBean(name = "pac4jAuthenticationEventExecutionPlanConfigurer")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public AuthenticationEventExecutionPlanConfigurer pac4jAuthenticationEventExecutionPlanConfigurer(
        @Qualifier("builtClients")
        final Clients builtClients,
        @Qualifier("clientAuthenticationHandler")
        final AuthenticationHandler clientAuthenticationHandler,
        @Qualifier("clientAuthenticationMetaDataPopulator")
        final AuthenticationMetaDataPopulator clientAuthenticationMetaDataPopulator,
        @Qualifier("defaultPrincipalResolver")
        final PrincipalResolver defaultPrincipalResolver) {
        return plan -> {
            if (!builtClients.findAllClients().isEmpty()) {
                LOGGER.info("Registering delegated authentication clients...");
                plan.registerAuthenticationHandlerWithPrincipalResolver(clientAuthenticationHandler, defaultPrincipalResolver);
                plan.registerAuthenticationMetadataPopulator(clientAuthenticationMetaDataPopulator);
            }
        };
    }

    @ConditionalOnMissingBean(name = "delegatedAuthenticationAuditResourceResolver")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public AuditResourceResolver delegatedAuthenticationAuditResourceResolver() {
        return new DelegatedAuthenticationAuditResourceResolver();
    }

    @Bean
    @ConditionalOnMissingBean(name = "delegatedAuthenticationAuditTrailRecordResolutionPlanConfigurer")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public AuditTrailRecordResolutionPlanConfigurer delegatedAuthenticationAuditTrailRecordResolutionPlanConfigurer(
        @Qualifier("delegatedAuthenticationAuditResourceResolver")
        final AuditResourceResolver delegatedAuthenticationAuditResourceResolver,
        @Qualifier("authenticationActionResolver")
        final AuditActionResolver authenticationActionResolver) {
        return plan -> {
            plan.registerAuditActionResolver(AuditActionResolvers.DELEGATED_CLIENT_ACTION_RESOLVER, authenticationActionResolver);
            plan.registerAuditResourceResolver(AuditResourceResolvers.DELEGATED_CLIENT_RESOURCE_RESOLVER, delegatedAuthenticationAuditResourceResolver);
        };
    }

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @ConditionalOnMissingBean(name = "delegatedAuthenticationLogoutExecutionPlanConfigurer")
    @Autowired
    public LogoutExecutionPlanConfigurer delegatedAuthenticationLogoutExecutionPlanConfigurer(
        final CasConfigurationProperties casProperties,
        @Qualifier("delegatedClientDistributedSessionStore")
        final SessionStore delegatedClientDistributedSessionStore) {
        return plan -> {
            val replicate = casProperties.getAuthn().getPac4j().getCore().isReplicateSessions();
            if (replicate) {
                plan.registerLogoutPostProcessor(ticketGrantingTicket -> {
                    val request = HttpRequestUtils.getHttpServletRequestFromRequestAttributes();
                    val response = HttpRequestUtils.getHttpServletResponseFromRequestAttributes();
                    if (request != null && response != null) {
                        val store = delegatedClientDistributedSessionStore;
                        store.destroySession(new JEEContext(request, response));
                    }
                });
            }
        };
    }
}
