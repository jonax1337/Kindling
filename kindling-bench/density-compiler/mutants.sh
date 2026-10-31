#!/bin/sh
# Mutation check of the verifier: every mutant is a copy of the sources with one comparison or guard broken the way a
# real slip would break it. `verify --synthetic-only` must fail on each one; a mutant that passes means the verifier
# cannot see that kind of mistake. (A mutant must change the value of some output; one that only changes what is
# computed for a NaN alpha is equivalent, since NaN * x is NaN, and does not belong here.) Mutants live in java-build/mutants/ and are rebuilt on every run.
set -e

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$HERE/java-build/mutants
SURVIVORS=0
COUNT=0

# mutant NAME FILE SED-EXPRESSION...: build the mutated copy and run the synthetic verify on it.
mutant() {
  name=$1
  file=$2
  shift 2
  dir=$ROOT/$name
  mkdir -p "$dir/src"
  cp -r "$HERE/java/." "$dir/src/"
  target=$dir/src/densitycompiler/$file
  sed "$@" "$target" > "$target.mutated"
  if cmp -s "$target" "$target.mutated"; then
    echo "mutants.sh: $name does not change $file" >&2
    exit 2
  fi
  mv "$target.mutated" "$target"
  SOURCE_DIR=$dir/src CLASSES_DIR=$dir/classes sh "$HERE/run.sh" build > "$dir/build.log" 2>&1 || { echo "mutants.sh: $name does not compile, see $dir/build.log" >&2; exit 2; }
  COUNT=$((COUNT + 1))
  if CLASSES_DIR=$dir/classes sh "$HERE/run.sh" verify --synthetic-only > "$dir/verify.log" 2>&1; then
    echo "SURVIVED  $name ($file)"
    SURVIVORS=$((SURVIVORS + 1))
  else
    echo "killed    $name: $(grep -c ' -> DIFFERENT' "$dir/verify.log") failing tallies"
  fi
}

mutant range_choice_min_exclusive eval/RangeChoiceNode.java -e 's/>= this\.minInclusive/> this.minInclusive/g'
mutant range_choice_max_inclusive eval/RangeChoiceNode.java -e 's/< this\.maxExclusive/<= this.maxExclusive/g'
mutant range_choice_nan_in_range eval/RangeChoiceNode.java \
  -e 's/boolean isIn = input\[i\] >= this\.minInclusive \&\& input\[i\] < this\.maxExclusive;/boolean isIn = !(input[i] < this.minInclusive) \&\& !(input[i] >= this.maxExclusive);/' \
  -e 's/(!(value >= this\.minInclusive) || !(value < this\.maxExclusive))/(value < this.minInclusive || value >= this.maxExclusive)/'
mutant const_range_choice_min_exclusive eval/ConstRangeChoiceNode.java -e 's/input >= minInclusive/input > minInclusive/'
mutant interval_select_threshold_inclusive eval/IntervalSelectNode.java -e 's/input < this\.thresholds\[i\]/input <= this.thresholds[i]/'
mutant range_choice_tile_min_exclusive eval/RangeChoiceTile.java -e 's/>= this\.minInclusive/> this.minInclusive/g'
mutant range_choice_tile_max_inclusive eval/RangeChoiceTile.java -e 's/< this\.maxExclusive/<= this.maxExclusive/g'
mutant lerp_first_side_skipped_at_alpha_zero eval/LerpNode.java -e 's/sideDemand(frame, demand, alphas, 1\.0F)/sideDemand(frame, demand, alphas, 0.0F)/'
mutant lerp_alpha_one_inclusive eval/LerpNode.java -e 's/alpha == 1\.0F/alpha >= 1.0F/'
mutant lerp_alpha_zero_inclusive eval/LerpNode.java -e 's/alpha == 0\.0F/alpha <= 0.0F/'
mutant cache_trusts_finished_context eval/CacheNode.java -e 's/this\.context == frame\.context() \&\& region\.equals(this\.volume)/region.equals(this.volume)/'

echo "$COUNT mutants, $SURVIVORS survived"
[ "$SURVIVORS" -eq 0 ]
