Contributing
============

Please read the following documents to contribute.

Git flow
--------

Please, refer to [Gitlab Flow](https://about.gitlab.com/2014/09/29/gitlab-flow)

Flow summary:

1. push your feature/fix branches to origin (see branch naming hereafter)
    > code from your branch is auto deployed to a specific location on a staging server (Gitlab Review Apps)
2. create a merge request from your branch to master on Gitlab 
3. when tests pass and code review is done: click to merge 
    > code from master branch is auto deployed to staging
4. merge master branch in pre-production branch
    > code from pre-production branch is auto deployed to pre-production server
5. merge pre-production branch in production branch
    > code from production branch is ready to be manually deployed to production

Master, pre-production and production branches are protected.

Branch naming
-------------

The branch naming format must be:

`<karma type>_<gitlab issue number>_<short_name>`

* karma type: refer to [Karma conventions](http://karma-runner.github.io/1.0/dev/git-commit-msg.html)
* issue number: a Gitlab issue number
* short name: a short title 

Type and short name should be lowercase.

Examples: 
* `fix_544_my_feature



Git commit message
------------------

Please, refer to [Karma conventions](http://karma-runner.github.io/1.0/dev/git-commit-msg.html)

Complementary informations:
* A git hook check the message format
* Allowed scopes: `toDo`

Git and binary files
--------------------

toDo: git-lfs


Reviewing Merge Request
-----------------------

* Ping team when a MR is ready to be reviewed (Slack)
* **Two approvals** are needed to merge 
* at the second approval the MR must be merged


Reporting a Bug
---------------
toDo


Running tests
-------------

`sbt test`

Coding Standards
----------------

toDo

Production hotfix
-----------------

toDo