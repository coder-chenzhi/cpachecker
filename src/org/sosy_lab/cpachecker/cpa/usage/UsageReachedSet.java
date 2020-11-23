// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Set;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.reachedset.PartitionedReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.WaitlistFactory;
import org.sosy_lab.cpachecker.cpa.usage.storage.ConcurrentUsageExtractor;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageConfiguration;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@SuppressFBWarnings(justification = "No support for serialization", value = "SE_BAD_FIELD")
public class UsageReachedSet extends PartitionedReachedSet {

  private static final long serialVersionUID = 1L;

  private boolean usagesExtracted = false;

  public static class RaceProperty implements Property {
    @Override
    public String toString() {
      return "Race condition";
    }
  }

  private static final ImmutableSet<Property> RACE_PROPERTY = ImmutableSet.of(new RaceProperty());

  private final LogManager logger;
  private final boolean processCoveredUsages;
  private ConcurrentUsageExtractor extractor = null;

  private final UsageContainer container;
  private List<Pair<UsageInfo, UsageInfo>> stableUnsafes = ImmutableList.of();

  public UsageReachedSet(
      WaitlistFactory waitlistFactory, UsageConfiguration pConfig, LogManager pLogger) {
    super(waitlistFactory);
    logger = pLogger;
    container = new UsageContainer(pConfig, logger);
    processCoveredUsages = pConfig.getProcessCoveredUsages();
  }

  @Override
  public void remove(AbstractState pState) {
    super.remove(pState);
    UsageState ustate = UsageState.get(pState);
    container.removeState(ustate);
  }

  @Override
  public void add(AbstractState pState, Precision pPrecision) {
    super.add(pState, pPrecision);

    /*UsageState USstate = UsageState.get(pState);
    USstate.saveUnsafesInContainerIfNecessary(pState);*/
  }

  @Override
  public void clear() {
    container.resetUnrefinedUnsafes();
    usagesExtracted = false;
    super.clear();
  }

  @Override
  public boolean hasViolatedProperties() {
    if (!usagesExtracted) {
      extractor.extractUsages(getFirstState());
      usagesExtracted = true;
      stableUnsafes = container.calculateStableUnsafes();
    }
    return !stableUnsafes.isEmpty();
  }

  @Override
  public Set<Property> getViolatedProperties() {
    if (hasViolatedProperties()) {
      return RACE_PROPERTY;
    } else {
      return ImmutableSet.of();
    }
  }

  public UsageContainer getUsageContainer() {
    return container;
  }

  public List<Pair<UsageInfo, UsageInfo>> getUnsafes() {
    return stableUnsafes;
  }

  private void writeObject(@SuppressWarnings("unused") ObjectOutputStream stream) {
    throw new UnsupportedOperationException("cannot serialize Logger");
  }

  @Override
  public void finalize(ConfigurableProgramAnalysis pCpa) {
    extractor = new ConcurrentUsageExtractor(pCpa, logger, container, processCoveredUsages);
  }

  public void printStatistics(StatisticsWriter pWriter) {
    extractor.printStatistics(pWriter);
  }

}
