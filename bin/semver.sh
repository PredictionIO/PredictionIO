#!/usr/bin/env sh
#
# Copyright (c) 2013, Ray Bejjani
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
# ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
# (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
# SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#
# The views and conclusions contained in the software and documentation are those
# of the authors and should not be interpreted as representing official policies,
# either expressed or implied, of the FreeBSD Project.

function semverParseInto() {
  local RE='[^0-9]*\([0-9]*\)[.]\([0-9]*\)[.]\([0-9]*\)\([0-9A-Za-z-]*\)'
  #MAJOR
  eval $2=`echo $1 | sed -e "s#$RE#\1#"`
  #MINOR
  eval $3=`echo $1 | sed -e "s#$RE#\2#"`
  #MINOR
  eval $4=`echo $1 | sed -e "s#$RE#\3#"`
  #SPECIAL
  eval $5=`echo $1 | sed -e "s#$RE#\4#"`
}

function semverEQ() {
  local MAJOR_A=0
  local MINOR_A=0
  local PATCH_A=0
  local SPECIAL_A=0

  local MAJOR_B=0
  local MINOR_B=0
  local PATCH_B=0
  local SPECIAL_B=0

  semverParseInto $1 MAJOR_A MINOR_A PATCH_A SPECIAL_A
  semverParseInto $2 MAJOR_B MINOR_B PATCH_B SPECIAL_B

  if [ $MAJOR_A -ne $MAJOR_B ]; then
    return 1
  fi

  if [ $MINOR_A -ne $MINOR_B ]; then
    return 1
  fi

  if [ $PATCH_A -ne $PATCH_B ]; then
    return 1
  fi

  if [[ "_$SPECIAL_A" != "_$SPECIAL_B" ]]; then
    return 1
  fi

  return 0
}

function semverLT() {
  local MAJOR_A=0
  local MINOR_A=0
  local PATCH_A=0
  local SPECIAL_A=0

  local MAJOR_B=0
  local MINOR_B=0
  local PATCH_B=0
  local SPECIAL_B=0

  semverParseInto $1 MAJOR_A MINOR_A PATCH_A SPECIAL_A
  semverParseInto $2 MAJOR_B MINOR_B PATCH_B SPECIAL_B

  if [ $MAJOR_A -lt $MAJOR_B ]; then
    return 0
  fi

  if [[ $MAJOR_A -le $MAJOR_B  && $MINOR_A -lt $MINOR_B ]]; then
    return 0
  fi

  if [[ $MAJOR_A -le $MAJOR_B  && $MINOR_A -le $MINOR_B && $PATCH_A -lt $PATCH_B ]]; then
    return 0
  fi

  if [[ "_$SPECIAL_A"  == "_" ]] && [[ "_$SPECIAL_B"  == "_" ]] ; then
    return 1
  fi
  if [[ "_$SPECIAL_A"  == "_" ]] && [[ "_$SPECIAL_B"  != "_" ]] ; then
    return 1
  fi
  if [[ "_$SPECIAL_A"  != "_" ]] && [[ "_$SPECIAL_B"  == "_" ]] ; then
    return 0
  fi

  if [[ "_$SPECIAL_A" < "_$SPECIAL_B" ]]; then
    return 0
  fi

  return 1

}

function semverGT() {
  semverEQ $1 $2
  local EQ=$?

  semverLT $1 $2
  local LT=$?

  if [ $EQ -ne 0 ] && [ $LT -ne 0 ]; then
    return 0
  else
    return 1
  fi
}

if [ "___semver.sh" == "___`basename $0`" ]; then
  MAJOR=0
  MINOR=0
  PATCH=0
  SPECIAL=""

  semverParseInto $1 MAJOR MINOR PATCH SPECIAL
  echo "$1 -> M: $MAJOR m:$MINOR p:$PATCH s:$SPECIAL"

  semverParseInto $2 MAJOR MINOR PATCH SPECIAL
  echo "$2 -> M: $MAJOR m:$MINOR p:$PATCH s:$SPECIAL"

  semverEQ $1 $2
  echo "$1 == $2 -> $?."

  semverLT $1 $2
  echo "$1 < $2 -> $?."

  semverGT $1 $2
  echo "$1 > $2 -> $?."
fi
