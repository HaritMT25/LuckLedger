#!/usr/bin/env bash
# One-shot assembly: bring the union of worktree-branch Java files onto main's working tree.
# Divergent files are sourced from the specifically-chosen branch (see comments).
set -euo pipefail
cd "$(dirname "$0")/.."

co() { # co <branch-suffix> <path...>
  local ref="origin/worktree-LuckLedger-$1"; shift
  git checkout "$ref" -- "$@"
}

D=luckledger-domain/src/main/java/com/luckledger/domain
DT=luckledger-domain/src/test/java/com/luckledger/domain
M=luckledger-mechanic/src/main/java/com/luckledger/mechanic
MT=luckledger-mechanic/src/test/java/com/luckledger/mechanic
P=luckledger-player/src/main/java/com/luckledger/player
PT=luckledger-player/src/test/java/com/luckledger/player

############ POOL (ay2) ############
co ay2.8 \
  $D/pool/PoolContract.java $D/pool/PoolFactory.java $D/pool/PoolValidator.java \
  $DT/pool/PoolContractTest.java $DT/pool/PoolFactoryTest.java $DT/pool/PoolValidatorTest.java
co ay2.9 $D/pool/LosingTier.java $DT/pool/LosingTierTest.java

############ PLAYER domain + bank (cti) ############
co cti.5 \
  $D/player/Player.java $DT/player/PlayerTest.java \
  $P/bank/BankService.java $PT/bank/BankServiceTest.java

############ MECHANIC (dbm) ############
# Celestial Fortune complete set + mechanic domain types + GridUtils, GameMechanic, pom
co dbm.22 luckledger-mechanic/pom.xml \
  $D/mechanic/Cell.java $D/mechanic/EvaluationResult.java $D/mechanic/Grid.java $D/mechanic/GridPopulator.java \
  $DT/mechanic/CellTest.java $DT/mechanic/EvaluationResultTest.java $DT/mechanic/GridTest.java $DT/mechanic/GridPopulatorTest.java \
  $M/CelestialFortuneEvaluator.java $M/CelestialFortuneMechanic.java $M/CelestialFortunePopulator.java \
  $M/GameMechanic.java $M/GridUtils.java $M/WinEvaluator.java \
  $MT/CelestialFortuneEvaluatorTest.java $MT/CelestialFortuneMechanicTest.java $MT/CelestialFortuneMonteCarloTest.java \
  $MT/CelestialFortunePopulatorTest.java $MT/GameMechanicTest.java $MT/GridUtilsTest.java $MT/WinEvaluatorTest.java
co dbm.16 $M/DemonSealPopulator.java $MT/DemonSealPopulatorTest.java
co dbm.17 $M/DemonSealEvaluator.java $MT/DemonSealEvaluatorTest.java
co dbm.21 $M/NearMissAnalyzer.java $MT/NearMissAnalyzerTest.java

############ LEDGER & INSIGHTS (7hs) ############
# All insight impls + core ledger types + consolidated test, from the all-insights branch
co 7hs.20 \
  $D/ledger/Insight.java $D/ledger/InsightGenerator.java $D/ledger/LedgerSnapshot.java $D/ledger/Transaction.java \
  $D/ledger/InevitabilityCurveInsight.java $D/ledger/LossChasingInsight.java $D/ledger/LossRateInsight.java \
  $D/ledger/LuckyStoreDebunkInsight.java $D/ledger/NearMissInsight.java $D/ledger/VarianceExplanationInsight.java \
  $DT/ledger/InsightGeneratorsTest.java
# LedgerSnapshotTest matching the 12-arg LedgerSnapshot lives on 7hs.14
co 7hs.14 $DT/ledger/LedgerSnapshotTest.java
# Core ledger tests (non-divergent) + player-ledger service + player pom, from 7hs.19
co 7hs.19 luckledger-player/pom.xml \
  $DT/ledger/InsightTest.java $DT/ledger/TransactionTest.java \
  $P/ledger/LedgerService.java $P/ledger/TransactionRecorder.java \
  $PT/ledger/LedgerServiceTest.java $PT/ledger/TransactionRecorderTest.java
# Individual insight tests (superseded by InsightGeneratorsTest but part of "all" the work)
co 7hs.10 $DT/ledger/LossRateInsightTest.java
co 7hs.11 $DT/ledger/LossChasingInsightTest.java
co 7hs.12 $DT/ledger/LuckyStoreDebunkInsightTest.java
co 7hs.13 $DT/ledger/VarianceExplanationInsightTest.java
co 7hs.14 $DT/ledger/NearMissInsightTest.java
co 7hs.15 $DT/ledger/InevitabilityCurveInsightTest.java

echo "ASSEMBLY DONE"
