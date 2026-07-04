package com.firstindemo.infra;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hazelcast 임베디드 설정.
 * 멀티캐스트 비활성화, 결과 캐시 맵 TTL 설정.
 */
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(HazelcastProperties.class)
public class HazelcastConfig {

  private final HazelcastProperties props;

  @Bean
  public Config hazelcast() {
    Config config = new Config();
    config.setInstanceName(props.instanceName());

    // 네트워크: 멀티캐스트 비활성화, TCP-IP 활성화 (로컬 데모)
    JoinConfig join = config.getNetworkConfig().getJoin();
    join.getMulticastConfig().setEnabled(false);
    join.getTcpIpConfig().setEnabled(true);
    props.members().forEach(member -> join.getTcpIpConfig().addMember(member));

    // 결과 캐시 맵 — TTL 적용
    MapConfig resultCacheMap = new MapConfig(CacheName.RESULT_CACHE.getMapName());
    resultCacheMap.setTimeToLiveSeconds(props.resultCache().ttlSeconds());
    config.addMapConfig(resultCacheMap);

    // 게이트 카운터 맵
    config.addMapConfig(new MapConfig(CacheName.GATE_COUNTER.getMapName()));

    // 이벤트 재고(stock) 캐시 맵
    config.addMapConfig(new MapConfig(CacheName.EVENT_STOCK.getMapName()));

    return config;
  }
}
