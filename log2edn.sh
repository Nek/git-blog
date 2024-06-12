#!/usr/bin/env bash
cd $1
echo "["
git log -10 --pretty=format:'{%n  :commit "%H"%n  :author "%aN <%aE>"%n  :date "%ad"%n  :message "%f"%n}'
echo "]"