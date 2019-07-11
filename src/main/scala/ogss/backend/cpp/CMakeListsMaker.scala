/*******************************************************************************
 * Copyright 2019 University of Stuttgart, Germany
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package ogss.backend.cpp

import java.io.File
import java.io.PrintWriter
import scala.collection.JavaConverters._

/**
 * Create CMakeLists.txt in the library directory to allow the user to modify
 * its behaviour.
 */
trait CMakeListsMaker extends AbstractBackEnd {
  abstract override def makeDeps {
    super.makeDeps

    // generate CmakeLists only if we alse create ogss.cpp.common
    if (!skipDependencies) {
      val out = new PrintWriter(new File(depsPath, "ogss.common.cpp/CMakeLists.txt"), "UTF-8")
      out.write(s"""# minimum is debian 8
cmake_minimum_required(VERSION 3.0.2)
project(ogss_common_cpp)

set(CMAKE_CXX_FLAGS "$${CMAKE_CXX_FLAGS} -std=c++11${
        if (cmakeNoWarn) "-w"
        else " -Wall -pedantic"
      }")

find_package (Threads REQUIRED)

################################
# Build common lib
################################

file(GLOB_RECURSE SOURCE_FILES LIST_DIRECTORIES false ogss/*.cpp)

# The resulting library to be used by generated code
ADD_LIBRARY(ogss.common.cpp STATIC $${SOURCE_FILES})

target_link_libraries (ogss.common.cpp $${CMAKE_THREAD_LIBS_INIT})

${
        if (cmakeFPIC) """set_property(TARGET ogss.common.cpp PROPERTY POSITION_INDEPENDENT_CODE ON)
"""
        else ""
      }set_property(TARGET ogss.common.cpp PROPERTY INTERPROCEDURAL_OPTIMIZATION True)""");
      out.close()
    }
  }
}
