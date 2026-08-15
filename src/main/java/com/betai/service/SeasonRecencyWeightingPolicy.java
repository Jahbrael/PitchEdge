package com.betai.service;

import java.math.BigDecimal;
import java.util.List;

public interface SeasonRecencyWeightingPolicy {

    String version();

    List<BigDecimal> weights(int seasonCount);
}
