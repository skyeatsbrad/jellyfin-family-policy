"""Real browser form tests against the disposable native API; Jellyfin ApiClient shim only."""
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
import json

def check_ui(base,admin,parent_id,root):
    html=(root/'src/FamilyPolicy.Plugin/Web/settings.html').read_text(encoding='utf-8')
    with sync_playwright() as p:
        browser=p.chromium.launch()
        page=browser.new_page()
        # The installed Jellyfin web client supplies this API interface. Fetch goes to the real test server.
        config={'base':base,'token':admin,'parent':parent_id}
        page.add_init_script("window.__testConfig="+json.dumps(config)+";"+'''
        window.ApiClient={
          getUrl:p=>__testConfig.base+'/'+p,
          getCurrentUserId:()=>__testConfig.parent,
          ajax:async o=>{const r=await fetch(o.url,{method:o.type||'GET',headers:{'Authorization':'MediaBrowser Token="'+__testConfig.token+'"','Content-Type':'application/json'},body:o.data});if(!r.ok)throw new Error('HTTP '+r.status);return r.status===204?undefined:r.json();},
          getUsers:()=>ApiClient.ajax({url:ApiClient.getUrl('Users')}),
          getItems:(id,o)=>ApiClient.ajax({url:ApiClient.getUrl('Items?'+new URLSearchParams({...o,UserId:id}))})
        };''')
        page.route(base+'/ui-test.html',lambda route:route.fulfill(status=200,body=html,content_type='text/html'))
        page.goto(base+'/ui-test.html')
        assert page.locator('#familyPolicyPage style').count()==1
        assert page.locator('fieldset.fpMatrixFieldset').count()==1
        page.evaluate("document.getElementById('familyPolicyPage').dispatchEvent(new Event('pageshow'))")
        expect(page.locator('#fpAccount option')).to_have_count(2)
        page.locator('#fpPg').click()
        page.locator('#fpZone').fill('UTC')
        page.locator('#fpKind').select_option('unrated-subject');page.locator('#fpValue').fill('Soccer');page.locator('#fpAdd').click()
        page.locator('#fpKind').select_option('franchise');page.locator('#fpValue').fill('Star Wars');page.locator('#fpAdd').click()
        page.locator('#fpKind').select_option('hours');page.locator('#fpAdd').click()
        page.locator('#fpKind').select_option('morning');page.locator('#fpValue').fill('Workout');page.locator('#fpAdd').click()
        draft=json.loads(page.locator('#fpPolicy').input_value())
        assert len(draft['Rules'])==5 and draft['DefaultTimeAllowed'] is False
        assert draft['Rules'][-1]['When']['All'][1]['Window']['End']=='08:00:00'
        page.locator('#fpSearch').fill('Soccer');page.locator('#fpFind').click()
        expect(page.locator('#fpMedia option')).to_have_count(1)
        page.locator('#fpPreview').click()
        expect(page.locator('#fpExplanation')).to_contain_text('Content: allowed')
        # Saving confirmed labels must preserve an unsaved draft and enforce revision checks.
        page.locator('#fpSubject').fill('Soccer');page.locator('#fpLabels').click()
        expect(page.locator('#fpResult')).to_contain_text('Parent-confirmed labels saved')
        assert len(json.loads(page.locator('#fpPolicy').input_value())['Rules'])==5
        page.locator('#fpRules button').last.click()
        assert len(json.loads(page.locator('#fpPolicy').input_value())['Rules'])==4
        # Matrix changes for another account must not discard the active account's draft.
        expect(page.locator('#fpMatrixBody tr')).to_have_count(5)
        mature=page.locator('#fpMatrixBody tr[data-item-name="Mature"]')
        mature_id=mature.get_attribute('data-item-id')
        child_b=mature.locator('select[data-user-name="ChildB"]')
        child_b_id=child_b.get_attribute('data-user-id')
        child_b.select_option('deny')
        page.locator('#fpMatrixApply').click()
        expect(page.locator('#fpResult')).to_contain_text('Matrix changes applied')
        assert len(json.loads(page.locator('#fpPolicy').input_value())['Rules'])==4
        state=page.evaluate('''async () => {
          const r=await fetch(__testConfig.base+'/FamilyPolicy/State',{headers:{'Authorization':'MediaBrowser Token="'+__testConfig.token+'"'}});
          return r.json();
        }''')
        child_policy=next(a['Policy'] for a in state['Accounts'] if a['UserId'].replace('-','').lower()==child_b_id.replace('-','').lower())
        assert any(r.get('Effect')=='Deny' and mature_id.replace('-','').lower() in [v.replace('-','').lower() for v in r.get('When',{}).get('Values',[])] for r in child_policy['Rules'])
        # A stale revision must leave both the matrix edit and unrelated active draft intact.
        safe=page.locator('#fpMatrixBody tr[data-item-name="Safe"]')
        safe_id=safe.get_attribute('data-item-id')
        safe_child_b=safe.locator('select[data-user-name="ChildB"]')
        safe_child_b.select_option('deny')
        bumped=page.evaluate('''async itemId => {
          const headers={'Authorization':'MediaBrowser Token="'+__testConfig.token+'"','Content-Type':'application/json'};
          const state=await (await fetch(__testConfig.base+'/FamilyPolicy/State',{headers})).json();
          const response=await fetch(__testConfig.base+'/FamilyPolicy/Labels/'+itemId,{method:'PUT',headers,
            body:JSON.stringify({ExpectedRevision:state.Revision,Labels:{Categories:['Revision conflict fixture']}})});
          return response.status;
        }''',safe_id)
        assert bumped==200
        page.locator('#fpMatrixApply').click()
        expect(page.locator('#fpResult')).to_contain_text('Matrix changes were not applied')
        expect(safe_child_b).to_have_value('deny')
        assert len(json.loads(page.locator('#fpPolicy').input_value())['Rules'])==4
        browser.close()
    return {'check':'Chromium rule-editor and library-matrix workflow','preset_and_advanced_rules':True,'preview_uses_native_api':True,
            'matrix_paging_and_update':True,'matrix_conflict_preserves_drafts':True,'label_save_preserves_draft':True}
