# PredictionIO Shared Shell Code

# This script should be sourced with $BASE set to the base of the binary package

VERSION=0.7.0-SNAPSHOT

# Play framework related
PLAY_OPTS=""
PLAY_START_OPTS="-Dio.prediction.base=$BASE -Dsbt.log.noformat=true -Dconfig.file=$BASE/conf/predictionio.conf"

# Log related
LOGDIR="$BASE/logs"

# Play apps related
ADMIN_PORT=9000
API_PORT=8000
SCHEDULER_PORT=7000

ADMIN_DIR="$BASE/servers/admin"
API_DIR="$BASE/servers/api"
SCHEDULER_DIR="$BASE/servers/scheduler"

ADMIN_OUT="$LOGDIR/admin.out"
API_OUT="$LOGDIR/api.out"
SCHEDULER_OUT="$LOGDIR/scheduler.out"

ADMIN_ERR="$LOGDIR/admin.err"
API_ERR="$LOGDIR/api.err"
SCHEDULER_ERR="$LOGDIR/scheduler.err"

# Kill the whole shell when Ctrl+C is pressed
trap "exit 1" INT

# Stop Play server
stop_play () {
	PLAY_NAME=$1
	PLAY_DIR=$2
	PLAY_OUT=$3
	mkdir -p `dirname $PLAY_OUT`
	echo "Trying to stop ${PLAY_NAME} server... \c"
	echo "Trying to stop ${PLAY_NAME} server at: `date`" >>"$PLAY_OUT"
	PID_FILE="${BASE}/${PLAY_NAME}.pid"
	#if [ -e $PLAY_DIR/RUNNING_PID ] ; then
	if [ -e $PID_FILE ] ; then
		#kill -TERM `cat ${PLAY_DIR}/RUNNING_PID`
		kill -TERM `cat $PID_FILE`
		echo "stopped"
	else
		#echo "cannot find ${PLAY_DIR}/RUNNING_PID (server may not be running)"
		echo "cannot find $PID_FILE (server may not be running)"
	fi
}
