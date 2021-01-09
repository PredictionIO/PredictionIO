#!/usr/bin/env bash
#

# A library to simplify using the SBT launcher from other packages.
# Note: This should be used by tools like giter8/conscript etc.

# TODO - Should we merge the main SBT script with this library?

declare -a residual_args
declare -a java_args
declare -a scalac_args
declare -a sbt_commands
declare java_cmd=java
declare java_version
declare -r sbt_bin_dir="$(dirname "$(realpath "$0")")"
declare -r sbt_home="$(dirname "$sbt_bin_dir")"
declare init_sbt_version=

echoerr () {
  echo 1>&2 "$@"
}
vlog () {
  [[ $verbose || $debug ]] && echoerr "$@"
}
dlog () {
  [[ $debug ]] && echoerr "$@"
}

jar_file () {
  echo "$(cygwinpath "${sbt_home}/bin/sbt-launch.jar")"
}

acquire_sbt_jar () {
  sbt_jar="$(jar_file)"

  if [[ ! -f "$sbt_jar" ]]; then
    echoerr "Could not find launcher jar: $sbt_jar"
    exit 2
  fi
}

rt_export_file () {
  echo "${sbt_bin_dir}/java9-rt-export.jar"
}

execRunner () {
  # print the arguments one to a line, quoting any containing spaces
  [[ $verbose || $debug ]] && echo "# Executing command line:" && {
    for arg; do
      if printf "%s\n" "$arg" | grep -q ' '; then
        printf "\"%s\"\n" "$arg"
      else
        printf "%s\n" "$arg"
      fi
    done
    echo ""
  }

  # THis used to be exec, but we loose the ability to re-hook stty then
  # for cygwin...  Maybe we should flag the feature here...
  "$@"
}

addJava () {
  dlog "[addJava] arg = '$1'"
  java_args=( "${java_args[@]}" "$1" )
}
addSbt () {
  dlog "[addSbt] arg = '$1'"
  sbt_commands=( "${sbt_commands[@]}" "$1" )
}
addResidual () {
  dlog "[residual] arg = '$1'"
  residual_args=( "${residual_args[@]}" "$1" )
}
addDebugger () {
  addJava "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$1"
}

get_mem_opts () {
  # if we detect any of these settings in ${JAVA_OPTS} or ${JAVA_TOOL_OPTIONS} we need to NOT output our settings.
  # The reason is the Xms/Xmx, if they don't line up, cause errors.
  if [[ "${JAVA_OPTS}" == *-Xmx* ]] || [[ "${JAVA_OPTS}" == *-Xms* ]] || [[ "${JAVA_OPTS}" == *-XX:MaxPermSize* ]] || [[ "${JAVA_OPTS}" == *-XX:MaxMetaspaceSize* ]] || [[ "${JAVA_OPTS}" == *-XX:ReservedCodeCacheSize* ]]; then
    echo ""
  elif [[ "${JAVA_TOOL_OPTIONS}" == *-Xmx* ]] || [[ "${JAVA_TOOL_OPTIONS}" == *-Xms* ]] || [[ "${JAVA_TOOL_OPTIONS}" == *-XX:MaxPermSize* ]] || [[ "${JAVA_TOOL_OPTIONS}" == *-XX:MaxMetaspaceSize* ]] || [[ "${JAVA_TOOL_OPTIONS}" == *-XX:ReservedCodeCacheSize* ]]; then
    echo ""
  elif [[ "${SBT_OPTS}" == *-Xmx* ]] || [[ "${SBT_OPTS}" == *-Xms* ]] || [[ "${SBT_OPTS}" == *-XX:MaxPermSize* ]] || [[ "${SBT_OPTS}" == *-XX:MaxMetaspaceSize* ]] || [[ "${SBT_OPTS}" == *-XX:ReservedCodeCacheSize* ]]; then
    echo ""
  else
    # a ham-fisted attempt to move some memory settings in concert
    # so they need not be messed around with individually.
    local mem=${1:-1024}
    local codecache=$(( $mem / 8 ))
    (( $codecache > 128 )) || codecache=128
    (( $codecache < 512 )) || codecache=512
    local class_metadata_size=$(( $codecache * 2 ))
    local class_metadata_opt=$([[ "$java_version" < "1.8" ]] && echo "MaxPermSize" || echo "MaxMetaspaceSize")

    local arg_xms=$([[ "${java_args[@]}" == *-Xms* ]] && echo "" || echo "-Xms${mem}m")
    local arg_xmx=$([[ "${java_args[@]}" == *-Xmx* ]] && echo "" || echo "-Xmx${mem}m")
    local arg_rccs=$([[ "${java_args[@]}" == *-XX:ReservedCodeCacheSize* ]] && echo "" || echo "-XX:ReservedCodeCacheSize=${codecache}m")
    local arg_meta=$([[ "${java_args[@]}" == *-XX:${class_metadata_opt}* && ! "$java_version" < "1.8" ]] && echo "" || echo "-XX:${class_metadata_opt}=${class_metadata_size}m")

    echo "${arg_xms} ${arg_xmx} ${arg_rccs} ${arg_meta}"
  fi
}

require_arg () {
  local type="$1"
  local opt="$2"
  local arg="$3"
  if [[ -z "$arg" ]] || [[ "${arg:0:1}" == "-" ]]; then
    echo "$opt requires <$type> argument"
    exit 1
  fi
}

is_function_defined() {
  declare -f "$1" > /dev/null
}

process_args () {
  while [[ $# -gt 0 ]]; do
    case "$1" in
       -h|-help) usage; exit 1 ;;
    -v|-verbose) verbose=1 && shift ;;
      -d|-debug) debug=1 && shift ;;

           -ivy) require_arg path "$1" "$2" && addJava "-Dsbt.ivy.home=$2" && shift 2 ;;
           -mem) require_arg integer "$1" "$2" && sbt_mem="$2" && shift 2 ;;
     -jvm-debug) require_arg port "$1" "$2" && addDebugger $2 && shift 2 ;;
         -batch) exec </dev/null && shift ;;

       -sbt-jar) require_arg path "$1" "$2" && sbt_jar="$2" && shift 2 ;;
   -sbt-version) require_arg version "$1" "$2" && sbt_version="$2" && shift 2 ;;
     -java-home) require_arg path "$1" "$2" &&
                 java_cmd="$2/bin/java" &&
                 export JAVA_HOME="$2" &&
                 export JDK_HOME="$2" &&
                 export PATH="$2/bin:$PATH" &&
                 shift 2 ;;

          "-D*") addJava "$1" && shift ;;
            -J*) addJava "${1:2}" && shift ;;
              *) addResidual "$1" && shift ;;
    esac
  done
  
  is_function_defined process_my_args && {
    myargs=("${residual_args[@]}")
    residual_args=()
    process_my_args "${myargs[@]}"
  }

  ## parses 1.7, 1.8, 9, etc out of java version "1.8.0_91"
  java_version=$("$java_cmd" -Xmx512M -version 2>&1 | sed 's/.*version "\([0-9]*\)\(\.[0-9]*\)\{0,1\}\(.*\)*"/\1\2/; 1q')
  vlog "[process_args] java_version = '$java_version'"
}

syncPreloaded() {
  [[ -f "$HOME/.sbt/preloaded/org.scala-sbt/sbt/$init_sbt_version/jars/sbt.jar" ]] || {
    # lib/local-preloaded exists (This is optional)
    [[ -d "$sbt_home/lib/local-preloaded/" ]] && {
      command -v rsync >/dev/null 2>&1 && {
        mkdir -p "$HOME/.sbt/preloaded"
        rsync -a --ignore-existing "$sbt_home/lib/local-preloaded/" "$HOME/.sbt/preloaded"
      }
    }
  }
}

# Detect that we have java installed.
checkJava() {
  local required_version="$1"
  # Now check to see if it's a good enough version
  if [[ "$java_version" == "" ]]; then
    echo
    echo No java installations was detected.
    echo Please go to http://www.java.com/getjava/ and download
    echo
    exit 1
  elif [[ "$java_version" < "$required_version" ]]; then
    echo
    echo The java installation you have is not up to date
    echo $script_name requires at least version $required_version+, you have
    echo version $java_version
    echo
    echo Please go to http://www.java.com/getjava/ and download
    echo a valid Java Runtime and install before running $script_name.
    echo
    exit 1
  fi
}

copyRt() {
  if [[ "$java_version" > "8" ]]; then
    rtexport=$(rt_export_file)
    java9_ext=$("$java_cmd" ${JAVA_OPTS} ${SBT_OPTS:-$default_sbt_opts} ${java_args[@]} \
      -jar "$rtexport" --rt-ext-dir)
    java9_rt=$(echo "$java9_ext/rt.jar")
    vlog "[copyRt] java9_rt = '$java9_rt'"
    if [[ ! -f "$java9_rt" ]]; then
      echo Copying runtime jar.
      mkdir -p "$java9_ext"
      execRunner "$java_cmd" \
        ${JAVA_OPTS} \
        ${SBT_OPTS:-$default_sbt_opts} \
        ${java_args[@]} \
        -jar "$rtexport" \
        "${java9_rt}"
    fi
    addJava "-Dscala.ext.dirs=${java9_ext}"
  fi
}

run() {
  syncPreloaded

  # no jar? download it.
  [[ -f "$sbt_jar" ]] || acquire_sbt_jar "$sbt_version" || {
    # still no jar? uh-oh.
    echo "Download failed. Obtain the sbt-launch.jar manually and place it at $sbt_jar"
    exit 1
  }

  # process the combined args, then reset "$@" to the residuals
  process_args "$@"
  set -- "${residual_args[@]}"
  argumentCount=$#

  # TODO - java check should be configurable...
  checkJava "1.6"

  # Java 9 support
  copyRt

  #If we're in cygwin, we should use the windows config, and terminal hacks
  if [[ "$CYGWIN_FLAG" == "true" ]]; then
    stty -icanon min 1 -echo > /dev/null 2>&1
    addJava "-Djline.terminal=jline.UnixTerminal"
    addJava "-Dsbt.cygwin=true"
  fi

  # run sbt
  execRunner "$java_cmd" \
    $(get_mem_opts $sbt_mem) \
    ${JAVA_OPTS} \
    ${SBT_OPTS:-$default_sbt_opts} \
    ${java_args[@]} \
    -jar "$sbt_jar" \
    "${sbt_commands[@]}" \
    "${residual_args[@]}"  
  
  exit_code=$?

  # Clean up the terminal from cygwin hacks.
  if [[ "$CYGWIN_FLAG" == "true" ]]; then
    stty icanon echo > /dev/null 2>&1
  fi
  exit $exit_code
}
