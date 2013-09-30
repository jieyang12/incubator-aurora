import subprocess


def build_and_execute_pex_target(target, binary):
  assert subprocess.call(["./pants", target]) == 0
  
  # TODO(wickman) Should we extract distdir from pants.ini?
  po = subprocess.Popen([binary, "--help"], stderr=subprocess.STDOUT, stdout=subprocess.PIPE)
  so, se = po.communicate()
  assert po.returncode == 1  # sigh
  assert so.startswith('Options')


def test_thermos_executor_build():
  build_and_execute_pex_target('src/python/twitter/aurora/executor/bin_internal:thermos_executor',
                               'dist/thermos_executor.pex')

def test_gc_executor_build():
  build_and_execute_pex_target('src/python/twitter/aurora/executor/bin_internal:gc_executor',
                               'dist/gc_executor.pex')

def test_angrybird_executor_build():
  build_and_execute_pex_target('src/python/twitter/aurora/executor/bin_internal:angrybird_executor',
                               'dist/angrybird_executor.pex')

def test_angrybird_gc_executor_build():
  build_and_execute_pex_target('src/python/twitter/aurora/executor/bin_internal:angrybird_gc_executor',
                               'dist/angrybird_gc_executor.pex')