package org.apereo.cas.config;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.model.support.jpa.JpaConfigurationContext;
import org.apereo.cas.configuration.support.JpaBeans;
import org.apereo.cas.jpa.JpaBeanFactory;
import org.apereo.cas.uma.ticket.resource.ResourceSet;
import org.apereo.cas.uma.ticket.resource.repository.ResourceSetRepository;
import org.apereo.cas.uma.ticket.resource.repository.impl.JpaResourceSetRepository;
import org.apereo.cas.util.CollectionUtils;

import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Set;

/**
 * This is {@link CasOAuthUmaJpaConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 6.0.0
 */
@EnableConfigurationProperties(CasConfigurationProperties.class)
@AutoConfigureBefore(CasOAuthUmaConfiguration.class)
@EnableTransactionManagement
@ConditionalOnProperty(name = "cas.authn.oauth.uma.resource-set.jpa.url")
@Configuration(value = "casOAuthUmaJpaConfiguration", proxyBeanMethods = false)
public class CasOAuthUmaJpaConfiguration {

    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Bean
    @Autowired
    public JpaVendorAdapter jpaUmaVendorAdapter(final CasConfigurationProperties casProperties,
                                                @Qualifier("jpaBeanFactory")
                                                final JpaBeanFactory jpaBeanFactory) {
        return jpaBeanFactory.newJpaVendorAdapter(casProperties.getJdbc());
    }

    @Bean
    public Set<String> jpaUmaPackagesToScan() {
        return CollectionUtils.wrapSet(ResourceSet.class.getPackage().getName());
    }

    @Lazy
    @Bean
    @Autowired
    public LocalContainerEntityManagerFactoryBean umaEntityManagerFactory(final CasConfigurationProperties casProperties,
                                                                          @Qualifier("jpaUmaVendorAdapter")
                                                                          final JpaVendorAdapter jpaUmaVendorAdapter,
                                                                          @Qualifier("dataSourceUma")
                                                                          final DataSource dataSourceUma,
                                                                          @Qualifier("jpaUmaPackagesToScan")
                                                                          final Set<String> jpaUmaPackagesToScan,
                                                                          @Qualifier("jpaBeanFactory")
                                                                          final JpaBeanFactory jpaBeanFactory) {
        val factory = jpaBeanFactory;
        val ctx = JpaConfigurationContext.builder()
            .jpaVendorAdapter(jpaUmaVendorAdapter)
            .persistenceUnitName(getClass().getSimpleName())
            .dataSource(dataSourceUma)
            .packagesToScan(jpaUmaPackagesToScan)
            .build();
        return factory.newEntityManagerFactoryBean(ctx, casProperties.getAuthn().getOauth().getUma().getResourceSet().getJpa());
    }

    @Autowired
    @Bean
    public PlatformTransactionManager umaTransactionManager(
        @Qualifier("umaEntityManagerFactory")
        final EntityManagerFactory emf) {
        val mgmr = new JpaTransactionManager();
        mgmr.setEntityManagerFactory(emf);
        return mgmr;
    }

    @Bean
    @ConditionalOnMissingBean(name = "dataSourceUma")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Autowired
    public DataSource dataSourceUma(final CasConfigurationProperties casProperties) {
        return JpaBeans.newDataSource(casProperties.getAuthn().getOauth().getUma().getResourceSet().getJpa());
    }

    @Bean
    public ResourceSetRepository umaResourceSetRepository() {
        return new JpaResourceSetRepository();
    }
}
