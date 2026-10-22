# k3s and Rancher with Ansible

Turns a plain Ubuntu 24.04 server into a single-node Kubernetes cluster (k3s) with Rancher to manage it:
base packages and kernel settings, k3s, Helm, cert-manager, then Rancher. All versions are pinned in
[`group_vars/k3s_servers.yml`](group_vars/k3s_servers.yml). The Request Portal chart can then be deployed
to it like to any cluster.

```bash
cp inventory.example.ini inventory.ini                  # set your server's address
ansible-vault create group_vars/k3s_servers.vault.yml   # vault_rancher_bootstrap_password: ...
ansible-galaxy collection install -r requirements.yml
ansible-playbook site.yml --ask-vault-pass
export KUBECONFIG=$PWD/kubeconfig-rancher-1.yaml        # fetched by the playbook; replace 127.0.0.1 with the server
```

> **Status: checked, not run.** The playbook passes `ansible-playbook --syntax-check` and `ansible-lint`,
> but it has not been run against a real server yet.
