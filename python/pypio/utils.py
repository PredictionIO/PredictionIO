#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


def dict_to_scalamap(jvm, d):
    """
    Convert python dictionary to scala type map

    :param jvm: sc._jvm
    :param d: python type dictionary
    """
    if d is None:
        return None
    sm = jvm.scala.Predef.Map().empty()
    for k, v in d.items():
        sm = sm.updated(k, v)
    return sm

def list_to_dict(l):
    """
    Convert python list to python dictionary

    :param l: python type list

    >>> list = ["key1", 1, "key2", 2, "key3", 3]
    >>> list_to_dict(list) == {'key1': 1, 'key2': 2, 'key3': 3}
    True
    """
    if l is None:
        return None
    return dict(zip(l[0::2], l[1::2]))


if __name__ == "__main__":
    import doctest
    import sys
    (failure_count, test_count) = doctest.testmod()
    if failure_count:
        sys.exit(-1)