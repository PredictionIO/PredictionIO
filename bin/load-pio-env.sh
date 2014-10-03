#!/usr/bin/env bash

# Copyright 2014 TappingStone, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script loads pio-env.sh if it exists, and ensures it is only loaded once.
# pio-env.sh is loaded from PIO_CONF_DIR if set, or within the current
# directory's conf/ subdirectory.

if [ -z "$PIO_ENV_LOADED" ]; then
  export PIO_ENV_LOADED=1

  # Returns the parent of the directory this script lives in.
  parent_dir="$(cd `dirname $0`/..; pwd)"

  use_conf_dir=${PIO_CONF_DIR:-"$parent_dir/conf"}

  if [ -f "${use_conf_dir}/pio-env.sh" ]; then
    # Promote all variable declarations to environment (exported) variables
    set -a
    . "${use_conf_dir}/pio-env.sh"
    set +a
  fi
fi
