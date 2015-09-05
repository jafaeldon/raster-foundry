# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf.urls import patterns, url

from apps.uploads import views


urlpatterns = patterns(
    '',
    url('^sign-request$', views.sign_request, name='sign_request'),
)
