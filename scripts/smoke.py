#!/usr/bin/env python3
"""Exercises real browser-style redirects and BFF/API security using only Python's standard library."""
import argparse,base64,concurrent.futures,http.cookiejar,json,time,urllib.request,urllib.parse,urllib.error
from html.parser import HTMLParser
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--federation',action='store_true');p.add_argument('--refresh',action='store_true');args=p.parse_args()
root=Path(__file__).resolve().parents[1]
env=dict(line.split('=',1) for line in (root/'.env').read_text().splitlines() if line and not line.startswith('#'))
BASE='http://localhost:8080'
class Form(HTMLParser):
    def __init__(self,html):super().__init__();self.csrf=None;self.feed(html)
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='input' and a.get('name')=='_csrf':self.csrf=a['value']
def browser():return urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def call(client,path,method='GET',data=None,headers=None):
    url=path if path.startswith('http') else BASE+path
    try:
        response=client.open(urllib.request.Request(url,data=data,headers=headers or {},method=method),timeout=15)
    except urllib.error.HTTPError as error:response=error
    body=response.read().decode();return response.status,body,response.geturl(),response.headers
checks=[]
def check(condition,name):
    if not condition:raise AssertionError(name)
    checks.append(name);print('PASS',name)
def login(username,federated=False):
    c=browser();status,html,url,_=call(c,'/oauth2/authorization/platform')
    check(status==200 and '/login' in url,'login redirects to identity server')
    if federated:
        status,html,url,_=call(c,'http://127.0.0.1:9000/oauth2/authorization/simulator')
        check(status==200 and url.startswith('http://127.0.0.1:9001/login'),'provider selection redirects to simulator')
    token=Form(html).csrf;check(bool(token),'identity login form has CSRF protection')
    target='http://127.0.0.1:9001/login' if federated else 'http://127.0.0.1:9000/login'
    status,body,url,_=call(c,target,'POST',urllib.parse.urlencode({'username':username,'password':env['DEMO_PASSWORD'],'_csrf':token}).encode(),{'Content-Type':'application/x-www-form-urlencoded'})
    status,body,_,_=call(c,'/bff/api/v1/session');session=json.loads(body)
    check(status==200 and session.get('authenticated') is True,f'{username} establishes BFF session')
    check('access_token' not in body and 'refresh_token' not in body,'session JSON exposes no OAuth tokens')
    return c
def csrf(c):
    status,body,_,_=call(c,'/bff/api/v1/csrf-token');check(status==200,'CSRF token endpoint succeeds');v=json.loads(body);return {v['headerName']:v['token']}
def logout(c):
    status,*_=call(c,'/bff/api/v1/session','DELETE',headers=csrf(c));check(status==204,'logout invalidates local session')
    status,body,_,_=call(c,'/bff/api/v1/session');check(not json.loads(body)['authenticated'],'logged-out browser is anonymous')
c=browser()
check(call(c,'/bff/api/v1/flights')[0]==401,'anonymous API request returns 401')
operator=login('operator')
status,body,_,_=call(operator,'/bff/api/v1/flights')
if status!=200:print('Business request failed',status,body[:400])
check(status==200 and [f['id'] for f in json.loads(body)['data']]==['FL-101'],'RBAC read and terminal visibility filter')
check(call(operator,'/bff/api/v1/flights/FL-101/delay-approvals','POST')[0]==403,'missing browser CSRF token returns 403')
check(call(operator,'/bff/api/v1/flights/FL-101/delay-approvals','POST',headers=csrf(operator))[0]==200,'operator may approve assigned T1 resource')
check(call(operator,'/bff/api/v1/flights/FL-202/delay-approvals','POST',headers=csrf(operator))[0]==403,'ABAC denies other terminal')
viewer=login('viewer')
check(call(viewer,'/bff/api/v1/flights/FL-101/delay-approvals','POST',headers=csrf(viewer))[0]==403,'RBAC denies viewer write even within assigned terminal')
if args.refresh:
    time.sleep(11)
    check(call(operator,'/bff/api/v1/flights')[0]==200,'expired access token refreshes without browser reauthentication')
logout(viewer);logout(operator)
if args.federation:
    external=login('sim-user',True)
    check(call(external,'/bff/api/v1/flights')[0]==200,'federated identity obtains application-issued API token')
    logout(external)
print(f'Completed {len(checks)} checks.')
