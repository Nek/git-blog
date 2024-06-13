#!/usr/bin/env bash
cd $1
echo "["
git log --pretty=format:'{%n:commit "%H"%n  :author "%aN <%aE>"%n  :date "%ad"%n  :body "%B"}'
echo "]"