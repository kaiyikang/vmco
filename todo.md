1. 我即使在另外一个目录下，也可以调用这个脚本，比如`bash <path_to_reviewm_script>`
2. 运行后，我遇到了无任何显示的情况，可以添加一个print提示，告诉user，当前状态
3. 默认先获得master最新的内容，然后再比较，因为比较旧的内容是无意义的
4. 不需要考虑当前在这个branch中，没有push和提交的内容，默认比较main和已经commit的修改